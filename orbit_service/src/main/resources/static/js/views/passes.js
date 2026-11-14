import { api } from '../api.js';
import { el, replaceChildren } from '../dom.js';
import { day, duration, number, relative, time } from '../format.js';
import { badge, card, clickable, emptyState, errorBox, field, numeric, numericHeading, row, table } from '../ui.js';
import { addFavourite, favourites, removeFavourite, selectStation, selectedStation } from '../state.js';

const SUGGESTIONS = [{ noradId: 25544, name: 'ISS (ZARYA)' }, { noradId: 69015, name: 'FRONTIERSAT' }];
const WINDOWS = [['P1D', 'Next 24 hours'], ['P2D', 'Next 2 days'], ['P3D', 'Next 3 days'], ['P7D', 'Next 7 days']];
const GOOD_ELEVATION_DEG = 20;
const EXCELLENT_ELEVATION_DEG = 45;
const SEARCH_DELAY_MILLIS = 250;

function passQuality(elevationDeg) {
    if (elevationDeg >= EXCELLENT_ELEVATION_DEG) {
        return badge('good', 'Excellent');
    }
    if (elevationDeg >= GOOD_ELEVATION_DEG) {
        return badge('warning', 'Good');
    }
    return badge('neutral', 'Low');
}

function satellitePicker(onPick) {
    const list = el('datalist', { id: `satellites-${Math.random().toString(36).slice(2)}` });
    const input = el('input', { type: 'search', placeholder: 'Name or NORAD ID', list: list.id, autocomplete: 'off' });
    const optionText = satellite => `${satellite.name} (${satellite.noradId})`;
    let found = [];
    let timer;
    const search = async () => {
        try {
            found = (await api.searchSatellites(input.value.trim(), 0, 10)).content;
        } catch {
            found = [];
        }
        list.replaceChildren(...found.map(satellite => el('option', { value: optionText(satellite) })));
    };
    input.addEventListener('input', () => {
        clearTimeout(timer);
        if (input.value.trim().length >= 2) {
            timer = setTimeout(search, SEARCH_DELAY_MILLIS);
        }
    });
    const pick = () => {
        const typed = input.value.trim();
        const match = found.find(satellite => typed === optionText(satellite) || typed === String(satellite.noradId));
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
        main.append(card(el('h2', {}, 'First, where are you?'),
            el('p', {}, 'Passes depend on where you listen from. Add your station first.'),
            el('a', { href: '#/stations' }, 'Add a station →')));
        return;
    }
    if (!stations.some(station => station.id === selectedStation())) {
        selectStation(stations[0].id);
    }
    const stationSelect = el('select', { onchange: () => { selectStation(stationSelect.value); load(); } },
        stations.map(station => el('option', { value: station.id, selected: station.id === selectedStation() }, station.name)));
    const windowSelect = el('select', { onchange: () => load() },
        WINDOWS.map(([value, label]) => el('option', { value }, label)));
    const goodOnly = el('input', { type: 'checkbox', onchange: () => load() });
    const chips = el('div', { class: 'chips' });
    const results = card();
    main.append(card(
        el('div', { class: 'row' }, field('Station', stationSelect), field('Window', windowSelect),
            el('label', { class: 'check' }, goodOnly, `Only passes above ${GOOD_ELEVATION_DEG}°`)),
        el('h3', { class: 'gap-top' }, 'Your satellites'), chips,
        satellitePicker(satellite => { addFavourite(satellite); load(); })), results);

    async function load() {
        const satellites = favourites();
        replaceChildren(chips, satelliteChips(satellites, load));
        if (satellites.length === 0) {
            replaceChildren(results, emptyState('Add a satellite to see its passes.'));
            return;
        }
        replaceChildren(results, emptyState('Predicting passes…'));
        const { passes, failures } = await predict(satellites, stationSelect.value, windowSelect.value);
        const shown = passes.filter(pass => !goodOnly.checked || pass.peak.elevationDeg >= GOOD_ELEVATION_DEG);
        replaceChildren(results, el('h2', {}, `${shown.length} passes`), failures.map(errorBox),
            shown.length === 0 ? emptyState('No passes in this window.') : passTable(shown, stationSelect.value));
    }

    await load();
}

function satelliteChips(satellites, onChange) {
    if (satellites.length === 0) {
        return [el('span', { class: 'muted' }, 'None yet. Try '), SUGGESTIONS.map(satellite => el('button', {
            class: 'link suggestion', type: 'button', onclick: () => { addFavourite(satellite); onChange(); }
        }, satellite.name))];
    }
    return satellites.map(satellite => el('span', { class: 'chip' }, satellite.name, el('button', {
        type: 'button', 'aria-label': `Remove ${satellite.name}`,
        onclick: () => { removeFavourite(satellite.noradId); onChange(); }
    }, '×')));
}

async function predict(satellites, station, period) {
    const answers = await Promise.allSettled(satellites.map(satellite => api.passes(satellite.noradId, station, period)));
    const passes = [];
    const failures = [];
    answers.forEach((answer, index) => {
        const satellite = satellites[index];
        if (answer.status === 'fulfilled') {
            passes.push(...answer.value.passes.map(pass => ({ ...pass, satellite })));
        } else {
            failures.push(new Error(`${satellite.name}: ${answer.reason.message}`));
        }
    });
    passes.sort((a, b) => new Date(a.start) - new Date(b.start));
    return { passes, failures };
}

function passTable(passes, station) {
    return table(['Rises', 'Satellite', numericHeading('Highest'), '', numericHeading('Duration'), 'Path across the sky'],
        passes.map(pass => row(clickable(() => open(pass, station)),
            [el('div', {}, `${day(pass.start)} ${time(pass.start)}`), el('div', { class: 'muted small' }, relative(pass.start))],
            pass.satellite.name,
            numeric(`${number(pass.peak.elevationDeg, 0)}°`),
            passQuality(pass.peak.elevationDeg),
            numeric(duration(new Date(pass.end) - new Date(pass.start))),
            `${pass.startDirection} → ${pass.peak.direction} → ${pass.endDirection}`)));
}

function open(pass, station) {
    const query = new URLSearchParams({
        satellite: pass.satellite.noradId, name: pass.satellite.name, station, start: pass.start, end: pass.end,
    });
    window.location.hash = `#/pass?${query}`;
}
