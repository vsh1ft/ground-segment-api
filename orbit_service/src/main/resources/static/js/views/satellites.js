import { api } from '../api.js';
import { dateTime, el, empty, errorBox, field, guarded, megahertz, number } from '../ui.js';
import { addFavourite, isFavourite, removeFavourite } from '../state.js';

export async function renderSatellites(main, params) {
    if (params.get('id')) {
        await renderSatellite(main, Number(params.get('id')));
        return;
    }
    main.append(el('h1', {}, 'Satellites'),
        el('p', { class: 'lead' }, 'The catalog of satellites passes can be predicted for. It refreshes itself from CelesTrak.'));
    const search = el('input', { type: 'search', placeholder: 'Name or NORAD ID' });
    const results = el('div');
    let page = 0;
    let timer;
    search.addEventListener('input', () => {
        clearTimeout(timer);
        timer = setTimeout(() => { page = 0; load(); }, 250);
    });
    const addId = el('input', { type: 'number', min: 1, placeholder: 'e.g. 69015' });
    main.append(el('div', { class: 'card' }, el('div', { class: 'row' }, el('div', { class: 'grow' }, field('Search', search)))),
        el('div', { class: 'card' }, results),
        el('div', { class: 'grid-2' },
            el('form', { class: 'card', onsubmit: async event => {
                event.preventDefault();
                await guarded(() => api.addSatellite(Number(addId.value)), 'Satellite added from CelesTrak');
                window.location.hash = `#/satellites?id=${addId.value}`;
            } }, el('h2', {}, 'Add from CelesTrak'), el('p', { class: 'muted small' }, 'For a satellite CelesTrak knows.'),
                el('div', { class: 'row' }, field('NORAD ID', addId), el('button', { class: 'primary', type: 'submit' }, 'Add'))),
            tleForm()));

    async function load() {
        try {
            const response = await api.searchSatellites(search.value.trim(), page, 20);
            results.replaceChildren(el('h2', {}, `${response.page.totalElements} satellites`));
            if (response.content.length === 0) {
                results.append(empty('Nothing found.'));
                return;
            }
            results.append(el('div', { class: 'table-wrap' }, el('table', {},
                el('thead', {}, el('tr', {}, el('th', {}, ''), el('th', {}, 'Name'), el('th', { class: 'num' }, 'NORAD ID'),
                    el('th', {}, 'Designator'), el('th', {}, 'Newest elements'))),
                el('tbody', {}, response.content.map(satellite => el('tr', {
                    class: 'clickable', onclick: () => { window.location.hash = `#/satellites?id=${satellite.noradId}`; }
                }, el('td', {}, star(satellite)), el('td', {}, satellite.name), el('td', { class: 'num' }, satellite.noradId),
                    el('td', {}, satellite.internationalDesignator), el('td', {}, dateTime(satellite.latestEpoch))))))),
            el('div', { class: 'pager' },
                el('button', { disabled: page === 0, onclick: () => { page--; load(); } }, '← Previous'),
                el('span', { class: 'muted small' }, `Page ${page + 1} of ${Math.max(1, response.page.totalPages)}`),
                el('button', { disabled: page + 1 >= response.page.totalPages, onclick: () => { page++; load(); } }, 'Next →')));
        } catch (error) {
            results.replaceChildren(errorBox(error));
        }
    }
    await load();
}

function star(satellite) {
    const button = el('button', { class: 'link', type: 'button', title: 'Show in my passes' });
    const paint = () => {
        button.textContent = isFavourite(satellite.noradId) ? '★' : '☆';
        button.setAttribute('aria-label', isFavourite(satellite.noradId) ? `Remove ${satellite.name} from my satellites`
            : `Add ${satellite.name} to my satellites`);
    };
    button.addEventListener('click', event => {
        event.stopPropagation();
        if (isFavourite(satellite.noradId)) {
            removeFavourite(satellite.noradId);
        } else {
            addFavourite(satellite);
        }
        paint();
    });
    paint();
    return button;
}

function tleForm(noradId) {
    const id = el('input', { type: 'number', min: 1, value: noradId, required: true });
    const tle = el('textarea', { rows: 4, required: true, placeholder: 'NAME\n1 99999U ...\n2 99999 ...' });
    return el('form', { class: 'card', onsubmit: async event => {
        event.preventDefault();
        await guarded(() => api.uploadTle(Number(id.value), tle.value), 'Orbital elements stored');
        window.location.hash = `#/satellites?id=${id.value}&t=${Date.now()}`;
    } }, el('h2', {}, 'Upload a TLE'),
        el('p', { class: 'muted small' }, 'For a new launch CelesTrak doesn\'t list yet, or your own orbit. Name line plus two element lines.'),
        noradId ? null : field('NORAD ID', id), field('TLE', tle),
        el('div', { class: 'row', style: 'margin-top: 8px' }, el('button', { class: 'primary', type: 'submit' }, 'Upload')));
}

async function renderSatellite(main, noradId) {
    main.append(el('a', { href: '#/satellites', class: 'small' }, '← Satellites'));
    let satellite;
    try {
        satellite = await api.satellite(noradId);
    } catch (error) {
        main.append(errorBox(error));
        return;
    }
    main.append(el('div', { class: 'spread' }, el('h1', {}, star(satellite), ' ', satellite.name),
        el('div', { class: 'row' },
            el('a', { href: `#/telemetry?satellite=${noradId}` }, 'Telemetry'),
            el('button', { class: 'danger', onclick: async () => {
                if (confirm(`Remove ${satellite.name} from the catalog?`)) {
                    await guarded(() => api.deleteSatellite(noradId), 'Satellite removed');
                    window.location.hash = '#/satellites';
                }
            } }, 'Remove'))),
        el('div', { class: 'card' }, el('div', { class: 'stats' },
            stat(satellite.noradId, 'NORAD ID'), stat(satellite.internationalDesignator, 'International designator'),
            stat(dateTime(satellite.latestEpoch), 'Newest elements'), stat(dateTime(satellite.addedAt), 'In the catalog since'))));
    const transmitters = el('div', { class: 'card' }, el('h2', {}, 'Active transmitters'));
    main.append(transmitters, tleForm(noradId));
    try {
        const list = await api.transmitters(noradId);
        if (list.length === 0) {
            transmitters.append(empty('SatNOGS lists no active transmitters.'));
            return;
        }
        transmitters.append(el('div', { class: 'table-wrap' }, el('table', {},
            el('thead', {}, el('tr', {}, ['Description', 'Downlink', 'Uplink', 'Mode', 'Baud'].map(h => el('th', {}, h)))),
            el('tbody', {}, list.map(transmitter => el('tr', {}, el('td', {}, transmitter.description),
                el('td', {}, transmitter.downlinkLowHz ? megahertz(transmitter.downlinkLowHz) : '—'),
                el('td', {}, transmitter.uplinkLowHz ? megahertz(transmitter.uplinkLowHz) : '—'),
                el('td', {}, transmitter.mode ?? '—'), el('td', {}, transmitter.baud ? number(transmitter.baud, 0) : '—')))))),
            el('p', { class: 'muted small' }, 'Transmitter data from the SatNOGS DB.'));
    } catch (error) {
        transmitters.append(errorBox(error));
    }
}

function stat(value, label) {
    return el('div', { class: 'stat' }, el('div', { class: 'value' }, value), el('div', { class: 'label' }, label));
}
