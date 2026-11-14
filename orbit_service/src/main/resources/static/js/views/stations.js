import { api } from '../api.js';
import { el, replaceChildren } from '../dom.js';
import { number } from '../format.js';
import {
    card, emptyState, errorBox, field, formValues, notify, numeric, numericHeading, row, showingErrors, table
} from '../ui.js';
import { selectStation, selectedStation } from '../state.js';

const DEFAULT_MIN_ELEVATION_DEG = 5;

function stationForm(onSaved, station) {
    const input = (name, attributes) => el('input', { name, value: station?.[name], required: true, ...attributes });
    const locationError = el('div');
    const form = el('form', { class: 'card' },
        el('h2', {}, station ? `Edit ${station.name}` : 'Add a station'),
        el('div', { class: 'row' },
            field('Name', input('name', { maxlength: 100 })),
            field('Latitude °', input('latitudeDeg', { type: 'number', step: 'any', min: -90, max: 90 })),
            field('Longitude °', input('longitudeDeg', { type: 'number', step: 'any', min: -180, max: 180 })),
            field('Altitude m', input('altitudeM', { type: 'number', step: 'any', value: station?.altitudeM ?? 0 })),
            field('Min elevation °', input('minElevationDeg', {
                type: 'number', step: 'any', min: -5, max: 90, value: station?.minElevationDeg ?? DEFAULT_MIN_ELEVATION_DEG
            }))),
        el('p', { class: 'muted small' }, 'Minimum elevation: how high a satellite must rise before a pass counts. '
            + 'Trees and buildings usually mean 5 to 10°.'),
        locationError,
        el('div', { class: 'row' },
            el('button', {
                type: 'button', disabled: !navigator.geolocation, onclick: () => useMyLocation(form, locationError)
            }, 'Use my location'),
            el('button', { class: 'primary', type: 'submit' }, station ? 'Save' : 'Add station')));
    form.addEventListener('submit', event => {
        event.preventDefault();
        save(form, station, onSaved);
    });
    return form;
}

function useMyLocation(form, errorSlot) {
    navigator.geolocation.getCurrentPosition(position => {
        errorSlot.replaceChildren();
        form.latitudeDeg.value = position.coords.latitude.toFixed(5);
        form.longitudeDeg.value = position.coords.longitude.toFixed(5);
        if (position.coords.altitude !== null) {
            form.altitudeM.value = Math.round(position.coords.altitude);
        }
    }, error => replaceChildren(errorSlot, errorBox(new Error(`Location unavailable: ${error.message}`))));
}

function save(form, station, onSaved) {
    const values = formValues(form);
    const body = {
        name: values.name,
        latitudeDeg: Number(values.latitudeDeg),
        longitudeDeg: Number(values.longitudeDeg),
        altitudeM: Number(values.altitudeM),
        minElevationDeg: Number(values.minElevationDeg),
    };
    if (station) {
        notify(() => api.updateStation(station.id, body), `Station ${body.name} saved`, onSaved);
        return;
    }
    notify(() => api.createStation(body), `Station ${body.name} added`, created => {
        if (!selectedStation()) {
            selectStation(created.id);
        }
        form.reset();
        onSaved();
    });
}

export async function renderStations(main) {
    main.append(el('h1', {}, 'Stations'),
        el('p', { class: 'lead' }, 'Where you listen from. Passes, tracks and Doppler are computed for a station.'));
    const list = card();
    const editor = el('div');
    main.append(list, editor);

    const edit = station => replaceChildren(editor, stationForm(refresh, station));
    const use = station => {
        selectStation(station.id);
        refresh();
    };
    const remove = station => {
        if (confirm(`Delete station ${station.name}?`)) {
            notify(() => api.deleteStation(station.id), 'Station deleted', refresh);
        }
    };

    async function refresh() {
        replaceChildren(list, el('h2', {}, 'Your stations'));
        edit();
        await showingErrors(list, async () => list.append(stationList(await api.stations(), { use, edit, remove })));
    }

    await refresh();
}

function stationList(stations, actions) {
    if (stations.length === 0) {
        return emptyState('No stations yet. Add the place you listen from below.');
    }
    const action = (label, onClick, kind = '') => el('button', { class: `link ${kind}`, onclick: onClick }, label);
    return table(['Name', numericHeading('Latitude'), numericHeading('Longitude'), numericHeading('Altitude'),
        numericHeading('Min elevation'), ''], stations.map(station => row({},
            [station.name, ' ', station.id === selectedStation() ? el('span', { class: 'badge neutral' }, 'In use') : null],
            numeric(`${number(station.latitudeDeg, 5)}°`),
            numeric(`${number(station.longitudeDeg, 5)}°`),
            numeric(`${number(station.altitudeM, 0)} m`),
            numeric(`${number(station.minElevationDeg, 1)}°`),
            numeric([action('Use', () => actions.use(station)), ' · ', action('Edit', () => actions.edit(station)), ' · ',
                action('Delete', () => actions.remove(station), 'danger')]))));
}
