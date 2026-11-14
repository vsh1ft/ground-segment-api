import { api } from '../api.js';
import { append, el, replaceChildren } from '../dom.js';
import { dateTime, megahertz, number } from '../format.js';
import {
    card, clickable, emptyState, errorBox, field, notify, numeric, numericHeading, row, showingErrors, stat, stats,
    table
} from '../ui.js';
import { addFavourite, isFavourite, removeFavourite } from '../state.js';

const PAGE_SIZE = 20;
const SEARCH_DELAY_MILLIS = 250;

export async function renderSatellites(main, params) {
    if (params.get('id')) {
        await renderSatellite(main, Number(params.get('id')));
        return;
    }
    main.append(el('h1', {}, 'Satellites'),
        el('p', { class: 'lead' }, 'The catalog of satellites passes can be predicted for. It refreshes itself from CelesTrak.'));
    const search = el('input', { type: 'search', placeholder: 'Name or NORAD ID' });
    const results = card();
    let page = 0;
    let timer;
    const load = async () => {
        try {
            replaceChildren(results, searchResults(await api.searchSatellites(search.value.trim(), page, PAGE_SIZE), turn));
        } catch (error) {
            replaceChildren(results, errorBox(error));
        }
    };
    const turn = pages => {
        page += pages;
        load();
    };
    search.addEventListener('input', () => {
        clearTimeout(timer);
        timer = setTimeout(() => turn(-page), SEARCH_DELAY_MILLIS);
    });
    main.append(card(el('div', { class: 'row' }, el('div', { class: 'grow' }, field('Search', search)))), results,
        el('div', { class: 'grid-2' }, addForm(), tleForm()));
    await load();
}

function searchResults(response, turn) {
    const { number: page, totalPages, totalElements } = response.page;
    if (response.content.length === 0) {
        return [el('h2', {}, `${totalElements} satellites`), emptyState('Nothing found.')];
    }
    return [el('h2', {}, `${totalElements} satellites`),
        table(['', 'Name', numericHeading('NORAD ID'), 'Designator', 'Newest elements'],
            response.content.map(satellite => row(clickable(() => open(satellite.noradId)),
                star(satellite), satellite.name, numeric(satellite.noradId), satellite.internationalDesignator,
                dateTime(satellite.latestEpoch)))),
        el('div', { class: 'pager' },
            el('button', { disabled: page === 0, onclick: () => turn(-1) }, '← Previous'),
            el('span', { class: 'muted small' }, `Page ${page + 1} of ${Math.max(1, totalPages)}`),
            el('button', { disabled: page + 1 >= totalPages, onclick: () => turn(1) }, 'Next →'))];
}

function open(noradId) {
    window.location.hash = `#/satellites?id=${noradId}`;
}

function star(satellite) {
    const button = el('button', { class: 'link', type: 'button', title: 'Show in my passes' });
    const paint = () => {
        const favourite = isFavourite(satellite.noradId);
        button.textContent = favourite ? '★' : '☆';
        button.setAttribute('aria-label', favourite ? `Remove ${satellite.name} from my satellites`
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

function addForm() {
    const noradId = el('input', { type: 'number', min: 1, placeholder: 'e.g. 69015' });
    return el('form', {
        class: 'card', onsubmit: event => {
            event.preventDefault();
            notify(() => api.addSatellite(Number(noradId.value)), 'Satellite added from CelesTrak', () => open(noradId.value));
        }
    }, el('h2', {}, 'Add from CelesTrak'), el('p', { class: 'muted small' }, 'For a satellite CelesTrak knows.'),
        el('div', { class: 'row' }, field('NORAD ID', noradId), el('button', { class: 'primary', type: 'submit' }, 'Add')));
}

function tleForm(ofNoradId) {
    const noradId = el('input', { type: 'number', min: 1, value: ofNoradId, required: true });
    const tle = el('textarea', { rows: 4, required: true, placeholder: 'NAME\n1 99999U ...\n2 99999 ...' });
    return el('form', {
        class: 'card', onsubmit: event => {
            event.preventDefault();
            notify(() => api.uploadTle(Number(noradId.value), tle.value), 'Orbital elements stored', () => {
                window.location.hash = `#/satellites?id=${noradId.value}&t=${Date.now()}`;
            });
        }
    }, el('h2', {}, 'Upload a TLE'),
        el('p', { class: 'muted small' }, 'For a new launch CelesTrak doesn\'t list yet, or your own orbit. Name line plus two element lines.'),
        ofNoradId ? null : field('NORAD ID', noradId), field('TLE', tle),
        el('div', { class: 'row gap-top' }, el('button', { class: 'primary', type: 'submit' }, 'Upload')));
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
    const remove = () => {
        if (confirm(`Remove ${satellite.name} from the catalog?`)) {
            notify(() => api.deleteSatellite(noradId), 'Satellite removed', () => {
                window.location.hash = '#/satellites';
            });
        }
    };
    const transmitters = card(el('h2', {}, 'Active transmitters'));
    main.append(el('div', { class: 'spread' }, el('h1', {}, star(satellite), ' ', satellite.name),
            el('div', { class: 'row' }, el('a', { href: `#/telemetry?satellite=${noradId}` }, 'Telemetry'),
                el('button', { class: 'danger', onclick: remove }, 'Remove'))),
        card(stats(stat(satellite.noradId, 'NORAD ID'), stat(satellite.internationalDesignator, 'International designator'),
            stat(dateTime(satellite.latestEpoch), 'Newest elements'), stat(dateTime(satellite.addedAt), 'In the catalog since'))),
        transmitters, tleForm(noradId));
    await showingErrors(transmitters, async () => append(transmitters, transmitterList(await api.transmitters(noradId))));
}

function transmitterList(transmitters) {
    if (transmitters.length === 0) {
        return emptyState('SatNOGS lists no active transmitters.');
    }
    const frequency = hz => hz ? megahertz(hz) : '—';
    return [table(['Description', 'Downlink', 'Uplink', 'Mode', 'Baud'], transmitters.map(transmitter => row({},
            transmitter.description, frequency(transmitter.downlinkLowHz), frequency(transmitter.uplinkLowHz),
            transmitter.mode ?? '—', transmitter.baud ? number(transmitter.baud, 0) : '—'))),
        el('p', { class: 'muted small' }, 'Transmitter data from the SatNOGS DB.')];
}
