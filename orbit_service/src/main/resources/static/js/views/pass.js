import { api } from '../api.js';
import { lineChart, skyPlot } from '../charts.js';
import { el, replaceChildren } from '../dom.js';
import { dateTime, duration, megahertz, number, relative, time } from '../format.js';
import { card, emptyState, errorBox, field, numeric, row, stat, stats, table } from '../ui.js';

const MAX_POINTS = 600;
const MARGIN_MILLIS = 120_000;

export async function renderPass(main, params) {
    const pass = {
        noradId: params.get('satellite'),
        station: params.get('station'),
        start: new Date(params.get('start')),
        end: new Date(params.get('end')),
    };
    main.append(el('a', { href: '#/passes', class: 'small' }, '← Upcoming passes'),
        el('h1', {}, params.get('name') ?? `Satellite ${pass.noradId}`), countdown(pass));
    const query = trackQuery(pass);
    let track;
    try {
        track = await api.track(pass.noradId, query);
    } catch (error) {
        main.append(errorBox(error));
        return;
    }
    const inPass = during(track.points, pass);
    const peak = highest(inPass);
    main.append(card(stats(
            stat(time(pass.start), 'Rises'), stat(`${number(peak.elevationDeg, 0)}° at ${time(peak.time)}`, 'Highest'),
            stat(time(pass.end), 'Sets'), stat(duration(pass.end - pass.start), 'Duration'),
            stat(`${number(peak.rangeKm, 0)} km`, 'Closest'))),
        el('div', { class: 'grid-2' },
            card(el('h2', {}, 'Where to point'),
                el('p', { class: 'muted small' }, 'Looking up from the centre: the edge is the horizon, north is up.'),
                skyPlot({ points: inPass })),
            dopplerCard(track, pass, query)));
}

function countdown({ start, end }) {
    const text = el('p', { class: 'lead' });
    const tick = () => {
        text.textContent = countdownText(start, end);
    };
    tick();
    const timer = setInterval(() => document.body.contains(text) ? tick() : clearInterval(timer), 1000);
    return text;
}

function countdownText(start, end) {
    const now = Date.now();
    if (now < start) {
        return `Rises ${relative(start)}, at ${time(start)}`;
    }
    if (now < end) {
        return `Overhead now, sets ${relative(end)}`;
    }
    return `This pass ended ${relative(end)}.`;
}

function trackQuery({ station, start, end }) {
    const seconds = Math.round((end - start + 2 * MARGIN_MILLIS) / 1000);
    return {
        station,
        from: new Date(start.getTime() - MARGIN_MILLIS).toISOString(),
        duration: `PT${seconds}S`,
        step: `PT${Math.max(1, Math.ceil(seconds / MAX_POINTS))}S`,
    };
}

function during(points, { start, end }) {
    const inside = points.filter(point => new Date(point.time) >= start && new Date(point.time) <= end);
    return inside.length > 1 ? inside : points;
}

function highest(points) {
    return points.reduce((best, point) => point.elevationDeg > best.elevationDeg ? point : best);
}

function dopplerCard(track, pass, query) {
    const content = el('div');
    const custom = el('input', { type: 'number', min: 1, step: 1, placeholder: 'e.g. 437800000' });
    const show = shown => replaceChildren(content, frequencies(shown, pass));
    const recalculate = async () => {
        try {
            show(await api.track(pass.noradId, { ...query, downlinkHz: custom.value ? Number(custom.value) : undefined }));
        } catch (error) {
            replaceChildren(content, errorBox(error));
        }
    };
    show(track);
    return card(el('h2', {}, 'Which frequency to use'), content,
        el('div', { class: 'row gap-top' }, field('Or a frequency of your own (Hz)', custom),
            el('button', { type: 'button', onclick: recalculate }, 'Calculate')),
        el('p', { class: 'muted small' }, `Elements from ${dateTime(track.elementsEpoch)}.`));
}

function frequencies(track, pass) {
    if (track.frequencies.length === 0) {
        return emptyState('No active transmitters are known for this satellite.');
    }
    const select = el('select', {}, track.frequencies.map((frequency, index) => el('option', { value: index },
        `${frequency.description} — ${frequency.direction.toLowerCase()} ${megahertz(frequency.nominalHz)}`)));
    const detail = el('div');
    const show = () => replaceChildren(detail, frequencyDetail(track, Number(select.value), pass));
    select.addEventListener('change', show);
    show();
    return [field('Transmitter', select), detail];
}

function frequencyDetail(track, index, pass) {
    const frequency = track.frequencies[index];
    const inPass = during(track.points, pass);
    const moments = [['At rise', inPass[0]], ['At the highest point', highest(inPass)], ['At set', inPass.at(-1)]];
    return [
        el('p', { class: 'muted small' }, frequency.direction === 'DOWNLINK'
            ? 'Tune your receiver to the shifted frequency: higher while the satellite comes closer, lower as it leaves.'
            : 'Transmit on the shifted frequency so the satellite hears its nominal one.'),
        table([], moments.map(([label, point]) =>
            row({}, `${label} (${time(point.time)})`, numeric(megahertz(point.frequenciesHz[index]))))),
        el('h3', { class: 'gap-top' }, 'Shift from nominal'),
        lineChart({
            title: 'Shift from nominal', unit: 'kHz', zeroLine: true, format: value => number(value, 2),
            points: track.points.map(point => ({
                t: point.time,
                v: (point.frequenciesHz[index] - frequency.nominalHz) / 1000,
                note: `${megahertz(point.frequenciesHz[index])} · El ${number(point.elevationDeg, 1)}°`,
            })),
        }),
    ];
}
