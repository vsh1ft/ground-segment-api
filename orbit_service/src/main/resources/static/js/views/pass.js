import { api } from '../api.js';
import { lineChart, skyPlot } from '../charts.js';
import { dateTime, duration, el, empty, errorBox, field, megahertz, number, relative, time } from '../ui.js';

const MAX_POINTS = 600;

export async function renderPass(main, params) {
    const noradId = params.get('satellite');
    const station = params.get('station');
    const start = new Date(params.get('start'));
    const end = new Date(params.get('end'));
    main.append(el('a', { href: '#/passes', class: 'small' }, '← Upcoming passes'),
        el('h1', {}, params.get('name') ?? `Satellite ${noradId}`));
    const countdown = el('p', { class: 'lead' });
    main.append(countdown);
    const tick = () => {
        const now = Date.now();
        countdown.textContent = now < start ? `Rises ${relative(start)}, at ${time(start)}`
            : now < end ? `Overhead now, sets ${relative(end)}` : `This pass ended ${relative(end)}.`;
    };
    tick();
    const timer = setInterval(() => document.body.contains(countdown) ? tick() : clearInterval(timer), 1000);

    const from = new Date(start.getTime() - 120_000);
    const total = end.getTime() - start.getTime() + 240_000;
    const stepSeconds = Math.max(1, Math.ceil(total / 1000 / MAX_POINTS));
    const query = { station, from: from.toISOString(), duration: `PT${Math.round(total / 1000)}S`, step: `PT${stepSeconds}S` };
    let track;
    try {
        track = await api.track(noradId, query);
    } catch (error) {
        main.append(errorBox(error));
        return;
    }
    const peak = track.points.reduce((best, point) => point.elevationDeg > best.elevationDeg ? point : best, track.points[0]);
    main.append(el('div', { class: 'card' }, el('div', { class: 'stats' },
        stat(time(start), 'Rises'), stat(`${number(peak.elevationDeg, 0)}° at ${time(peak.time)}`, 'Highest'),
        stat(time(end), 'Sets'), stat(duration(end - start), 'Duration'),
        stat(`${number(peak.rangeKm, 0)} km`, 'Closest'))));

    const doppler = el('div', { class: 'card' });
    main.append(el('div', { class: 'grid-2' },
        el('div', { class: 'card' }, el('h2', {}, 'Where to point'),
            el('p', { class: 'muted small' }, 'Looking up from the centre: the edge is the horizon, north is up.'),
            skyPlot({ points: during(track.points, start, end) })),
        doppler));
    renderDoppler(doppler, track, noradId, query, start, end);
}

function during(points, start, end) {
    const inside = points.filter(point => new Date(point.time) >= start && new Date(point.time) <= end);
    return inside.length > 1 ? inside : points;
}

function renderDoppler(container, track, noradId, query, start, end) {
    container.replaceChildren(el('h2', {}, 'Which frequency to use'));
    const custom = el('input', { type: 'number', min: 1, step: 1, placeholder: 'e.g. 437800000' });
    const reload = async () => {
        try {
            const value = custom.value ? Number(custom.value) : undefined;
            renderDoppler(container, await api.track(noradId, { ...query, downlinkHz: value }), noradId, query, start, end);
        } catch (error) {
            container.append(errorBox(error));
        }
    };
    if (track.frequencies.length === 0) {
        container.append(empty('No active transmitters are known for this satellite.'));
    } else {
        const select = el('select', {}, track.frequencies.map((frequency, i) =>
            el('option', { value: i }, `${frequency.description} — ${frequency.direction.toLowerCase()} ${megahertz(frequency.nominalHz)}`)));
        const chartBox = el('div');
        const show = () => {
            const index = Number(select.value);
            const frequency = track.frequencies[index];
            const pass = during(track.points, start, end);
            const first = pass[0];
            const last = pass.at(-1);
            const middle = pass.reduce((best, point) => point.elevationDeg > best.elevationDeg ? point : best);
            chartBox.replaceChildren(
                el('p', { class: 'muted small' }, frequency.direction === 'DOWNLINK'
                    ? 'Tune your receiver to the shifted frequency: higher while the satellite comes closer, lower as it leaves.'
                    : 'Transmit on the shifted frequency so the satellite hears its nominal one.'),
                el('table', {}, el('tbody', {},
                    [['At rise', first], ['At the highest point', middle], ['At set', last]].map(([label, point]) =>
                        el('tr', {}, el('td', {}, `${label} (${time(point.time)})`),
                            el('td', { class: 'num' }, megahertz(point.frequenciesHz[index])))))),
                el('h3', { style: 'margin-top: 14px' }, 'Shift from nominal'),
                lineChart({
                    title: 'Shift from nominal', unit: 'kHz', zeroLine: true,
                    points: track.points.map(point => ({ t: point.time, v: (point.frequenciesHz[index] - frequency.nominalHz) / 1000,
                        note: `${megahertz(point.frequenciesHz[index])} · El ${number(point.elevationDeg, 1)}°` })),
                    format: value => number(value, 2)
                }));
        };
        select.addEventListener('change', show);
        container.append(field('Transmitter', select), chartBox);
        show();
    }
    container.append(el('div', { class: 'row', style: 'margin-top: 14px' },
        field('Or a frequency of your own (Hz)', custom), el('button', { type: 'button', onclick: reload }, 'Calculate')),
        el('p', { class: 'muted small' }, `Elements from ${dateTime(track.elementsEpoch)}.`));
}

function stat(value, label) {
    return el('div', { class: 'stat' }, el('div', { class: 'value' }, value), el('div', { class: 'label' }, label));
}
