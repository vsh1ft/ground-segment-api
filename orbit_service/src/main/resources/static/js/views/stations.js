import { api } from '../api.js';
import { el, empty, errorBox, field, formValues, guarded, number } from '../ui.js';
import { selectStation, selectedStation } from '../state.js';

function stationForm(onSaved, station) {
    const input = (name, attributes) => el('input', { name, value: station?.[name], required: true, ...attributes });
    const form = el('form', { class: 'card' },
        el('h2', {}, station ? `Edit ${station.name}` : 'Add a station'),
        el('div', { class: 'row' },
            field('Name', input('name', { maxlength: 100 })),
            field('Latitude °', input('latitudeDeg', { type: 'number', step: 'any', min: -90, max: 90 })),
            field('Longitude °', input('longitudeDeg', { type: 'number', step: 'any', min: -180, max: 180 })),
            field('Altitude m', input('altitudeM', { type: 'number', step: 'any', value: station?.altitudeM ?? 0 })),
            field('Min elevation °', input('minElevationDeg', { type: 'number', step: 'any', min: -5, max: 90,
                value: station?.minElevationDeg ?? 5 }))),
        el('p', { class: 'muted small' }, 'Minimum elevation: how high a satellite must rise before a pass counts. '
            + 'Trees and buildings usually mean 5 to 10°.'),
        el('div', { class: 'row' },
            el('button', { type: 'button', onclick: () => {
                if (!navigator.geolocation) {
                    return;
                }
                navigator.geolocation.getCurrentPosition(position => {
                    form.latitudeDeg.value = position.coords.latitude.toFixed(5);
                    form.longitudeDeg.value = position.coords.longitude.toFixed(5);
                    if (position.coords.altitude !== null) {
                        form.altitudeM.value = Math.round(position.coords.altitude);
                    }
                }, error => form.append(errorBox(new Error(`Location unavailable: ${error.message}`))));
            } }, 'Use my location'),
            el('button', { class: 'primary', type: 'submit' }, station ? 'Save' : 'Add station')));
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const values = formValues(form);
        const body = { name: values.name, latitudeDeg: Number(values.latitudeDeg), longitudeDeg: Number(values.longitudeDeg),
            altitudeM: Number(values.altitudeM), minElevationDeg: Number(values.minElevationDeg) };
        if (station) {
            await guarded(() => api.updateStation(station.id, body), `Station ${body.name} saved`);
        } else {
            const created = await guarded(() => api.createStation(body), `Station ${body.name} added`);
            if (!selectedStation()) {
                selectStation(created.id);
            }
            form.reset();
        }
        onSaved();
    });
    return form;
}

export async function renderStations(main) {
    main.append(el('h1', {}, 'Stations'),
        el('p', { class: 'lead' }, 'Where you listen from. Passes, tracks and Doppler are computed for a station.'));
    const list = el('div', { class: 'card' });
    const editor = el('div');
    main.append(list, editor);

    const showAdd = () => editor.replaceChildren(stationForm(refresh));

    async function refresh() {
        list.replaceChildren(el('h2', {}, 'Your stations'));
        showAdd();
        try {
            const stations = await api.stations();
            if (stations.length === 0) {
                list.append(empty('No stations yet. Add the place you listen from below.'));
                return;
            }
            list.append(el('div', { class: 'table-wrap' }, el('table', {},
                el('thead', {}, el('tr', {}, ['Name', 'Latitude', 'Longitude', 'Altitude', 'Min elevation', '']
                    .map(heading => el('th', { class: heading && heading !== 'Name' ? 'num' : '' }, heading)))),
                el('tbody', {}, stations.map(station => el('tr', {},
                    el('td', {}, station.name, ' ', station.id === selectedStation()
                        ? el('span', { class: 'badge neutral' }, 'In use') : null),
                    el('td', { class: 'num' }, `${number(station.latitudeDeg, 5)}°`),
                    el('td', { class: 'num' }, `${number(station.longitudeDeg, 5)}°`),
                    el('td', { class: 'num' }, `${number(station.altitudeM, 0)} m`),
                    el('td', { class: 'num' }, `${number(station.minElevationDeg, 1)}°`),
                    el('td', { class: 'num' },
                        el('button', { class: 'link', onclick: () => { selectStation(station.id); refresh(); } }, 'Use'), ' · ',
                        el('button', { class: 'link', onclick: () => editor.replaceChildren(stationForm(refresh, station)) }, 'Edit'), ' · ',
                        el('button', { class: 'link danger', onclick: async () => {
                            if (confirm(`Delete station ${station.name}?`)) {
                                await guarded(() => api.deleteStation(station.id), 'Station deleted');
                                await refresh();
                            }
                        } }, 'Delete'))))))));
        } catch (error) {
            list.append(errorBox(error));
        }
    }
    await refresh();
}
