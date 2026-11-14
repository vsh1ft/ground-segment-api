import { api } from '../api.js';
import { day, duration, el, empty, errorBox, field, number, passQuality, relative, time } from '../ui.js';
import { addFavourite, favourites, removeFavourite, selectStation, selectedStation } from '../state.js';

const SUGGESTIONS = [{ noradId: 25544, name: 'ISS (ZARYA)' }, { noradId: 69015, name: 'FRONTIERSAT' }];

export function satellitePicker(onPick) {
    const list = el('datalist', { id: `satellites-${Math.random().toString(36).slice(2)}` });
    const input = el('input', { type: 'search', placeholder: 'Name or NORAD ID', list: list.id, autocomplete: 'off' });
    let found = [];
    let timer;
    input.addEventListener('input', () => {
        clearTimeout(timer);
        timer = setTimeout(async () => {
            if (input.value.trim().length < 2) {
                return;
            }
            try {
                found = (await api.searchSatellites(input.value.trim(), 0, 10)).content;
                list.replaceChildren(...found.map(satellite => el('option', { value: `${satellite.name} (${satellite.noradId})` })));
            } catch {
                found = [];
            }
        }, 250);
    });
    const pick = () => {
        const match = found.find(satellite => input.value === `${satellite.name} (${satellite.noradId})`
            || input.value.trim() === String(satellite.noradId));
        if (match) {
            onPick(match);
            input.value = '';
        }
    };
    input.addEventListener('change', pick);
    return el('div', { class: 'row' }, field('Add a satellite', input), list,
        el('button', { type: 'button', onclick: pick }, 'Add'));
}

export async function renderPasses(main) {
    main.append(el('h1', {}, 'Upcoming passes'),
        el('p', { class: 'lead' }, 'When your satellites rise above your station, how high they get, and which way they go.'));
    let stations;
    try {
        stations = await api.stations();
    } catch (error) {
        main.append(errorBox(error));
        return;
    }
    if (stations.length === 0) {
        main.append(el('div', { class: 'card' }, el('h2', {}, 'First, where are you?'),
            el('p', {}, 'Passes depend on where you listen from. Add your station first.'),
            el('a', { href: '#/stations' }, 'Add a station →')));
        return;
    }
    if (!stations.some(station => station.id === selectedStation())) {
        selectStation(stations[0].id);
    }
    const stationSelect = el('select', { onchange: () => { selectStation(stationSelect.value); load(); } },
        stations.map(station => el('option', { value: station.id, selected: station.id === selectedStation() }, station.name)));
    const durationSelect = el('select', { onchange: () => load() },
        [['P1D', 'Next 24 hours'], ['P2D', 'Next 2 days'], ['P3D', 'Next 3 days'], ['P7D', 'Next 7 days']]
            .map(([value, label]) => el('option', { value }, label)));
    const goodOnly = el('input', { type: 'checkbox', onchange: () => load() });
    const chips = el('div', { class: 'chips' });
    const results = el('div', { class: 'card' });
    main.append(el('div', { class: 'card' },
        el('div', { class: 'row' }, field('Station', stationSelect), field('Window', durationSelect),
            el('label', { class: 'row', style: 'flex-direction: row; align-items: center' }, goodOnly, 'Only passes above 20°')),
        el('h3', { style: 'margin-top: 16px' }, 'Your satellites'), chips,
        satellitePicker(satellite => { addFavourite(satellite); load(); })), results);

    async function load() {
        const list = favourites();
        chips.replaceChildren(...list.map(satellite => el('span', { class: 'chip' }, satellite.name,
            el('button', { type: 'button', 'aria-label': `Remove ${satellite.name}`, onclick: () => { removeFavourite(satellite.noradId); load(); } }, '×'))));
        if (list.length === 0) {
            chips.append(el('span', { class: 'muted' }, 'None yet. Try '),
                ...SUGGESTIONS.map(satellite => el('button', { class: 'link', type: 'button', style: 'margin-right: 8px',
                    onclick: () => { addFavourite(satellite); load(); } }, satellite.name)));
            results.replaceChildren(empty('Add a satellite to see its passes.'));
            return;
        }
        results.replaceChildren(empty('Predicting passes…'));
        const answers = await Promise.allSettled(list.map(satellite =>
            api.passes(satellite.noradId, stationSelect.value, durationSelect.value)
                .then(response => response.passes.map(pass => ({ ...pass, satellite })))));
        const passes = answers.filter(answer => answer.status === 'fulfilled').flatMap(answer => answer.value)
            .filter(pass => !goodOnly.checked || pass.peak.elevationDeg >= 20)
            .sort((a, b) => new Date(a.start) - new Date(b.start));
        const failures = answers.map((answer, i) => answer.status === 'rejected'
            ? errorBox(new Error(`${list[i].name}: ${answer.reason.message}`)) : null);
        results.replaceChildren(el('h2', {}, `${passes.length} passes`), ...failures.filter(Boolean));
        if (passes.length === 0) {
            results.append(empty('No passes in this window.'));
            return;
        }
        results.append(el('div', { class: 'table-wrap' }, el('table', {},
            el('thead', {}, el('tr', {}, el('th', {}, 'Rises'), el('th', {}, 'Satellite'), el('th', { class: 'num' }, 'Highest'),
                el('th', {}, ''), el('th', { class: 'num' }, 'Duration'), el('th', {}, 'Path across the sky'))),
            el('tbody', {}, passes.map(pass => el('tr', {
                class: 'clickable', tabindex: 0,
                onclick: () => open(pass),
                onkeydown: event => event.key === 'Enter' && open(pass)
            },
                el('td', {}, el('div', {}, `${day(pass.start)} ${time(pass.start)}`), el('div', { class: 'muted small' }, relative(pass.start))),
                el('td', {}, pass.satellite.name),
                el('td', { class: 'num' }, `${number(pass.peak.elevationDeg, 0)}°`),
                el('td', {}, passQuality(pass.peak.elevationDeg)),
                el('td', { class: 'num' }, duration(new Date(pass.end) - new Date(pass.start))),
                el('td', {}, `${pass.startDirection} → ${pass.peak.direction} → ${pass.endDirection}`)))))));
    }

    function open(pass) {
        const query = new URLSearchParams({ satellite: pass.satellite.noradId, name: pass.satellite.name,
            station: stationSelect.value, start: pass.start, end: pass.end });
        window.location.hash = `#/pass?${query}`;
    }

    await load();
}
