import { api } from '../../api.js';
import { el, replaceChildren } from '../../dom.js';
import { dateTime } from '../../format.js';
import { badge, card, emptyState, errorBox, field, notify, row, table } from '../../ui.js';

const SOURCES = [['', 'Everyone'], ['SATNOGS', 'SatNOGS stations'], ['OWN', 'My stations']];
const COLUMNS = ['Received', 'Content', 'Measured', 'CRC', 'Heard by'];
const PAGE_SIZE = 25;
const RAW_PREVIEW_CHARACTERS = 24;

export async function renderFrames(container, noradId) {
    const source = el('select', {}, SOURCES.map(([value, label]) => el('option', { value }, label)));
    const list = card();
    const reload = () => {
        list.replaceChildren();
        return loadPage(list, noradId, source.value);
    };
    source.addEventListener('change', reload);
    container.append(card(el('div', { class: 'row' }, field('Received by', source)),
            el('p', { class: 'muted small' }, 'SatNOGS data is published under CC BY-SA 4.0 by the stations listed with each frame.')),
        list, await uploadForm(noradId, reload));
    await reload();
}

async function loadPage(list, noradId, source, before) {
    let response;
    try {
        response = await api.frames(noradId, { limit: PAGE_SIZE, before, source });
    } catch (error) {
        list.append(errorBox(error));
        return;
    }
    if (!before && response.frames.length === 0) {
        list.append(emptyState('No frames received yet.'));
        return;
    }
    list.append(table(before ? [] : COLUMNS, response.frames.map(frameRow)));
    if (response.nextBefore) {
        const more = el('div', { class: 'pager' }, el('button', {
            onclick: () => {
                more.remove();
                loadPage(list, noradId, source, response.nextBefore);
            }
        }, 'Older frames'));
        list.append(more);
    }
}

function frameRow(frame) {
    return row({},
        dateTime(frame.firstReceivedAt),
        [frame.container ?? el('span', { class: 'muted' }, 'not decoded'),
            el('div', { class: 'mono muted' }, `${frame.raw.slice(0, RAW_PREVIEW_CHARACTERS)}… (${frame.length} bytes)`)],
        frame.generationTime ? dateTime(frame.generationTime) : '—',
        crcBadge(frame.crcChecked),
        el('div', { class: 'chips' }, frame.receptions.map(receptionChip)));
}

function crcBadge(crcChecked) {
    if (crcChecked === null) {
        return '—';
    }
    return crcChecked ? badge('good', 'Checked') : badge('neutral', 'Not sent');
}

function receptionChip(reception) {
    const own = reception.source === 'OWN';
    return el('span', {
        class: 'chip', title: `${own ? 'Your station' : 'SatNOGS station'}, ${dateTime(reception.receivedAt)}`
    }, own ? '★ ' : '', reception.station);
}

async function uploadForm(noradId, onUploaded) {
    const stations = await api.stations().catch(() => []);
    const station = el('select', { required: true }, stations.map(item => el('option', { value: item.id }, item.name)));
    const receivedAt = el('input', { type: 'datetime-local', step: 1, required: true, value: localNow() });
    const frame = el('textarea', { rows: 3, required: true, placeholder: 'C2A28A0021435453...' });
    const upload = () => notify(() => api.uploadFrame(noradId, {
        station: station.value,
        receivedAt: new Date(receivedAt.value).toISOString(),
        frame: frame.value.replace(/\s+/g, ''),
    }), 'Frame stored', () => {
        frame.value = '';
        onUploaded();
    });
    return el('form', {
        class: 'card', onsubmit: event => {
            event.preventDefault();
            upload();
        }
    }, el('h2', {}, 'Add a frame your station received'),
        el('p', { class: 'muted small' }, 'Paste the frame in hex as your decoder (SatDump, gr-satellites…) wrote it.'),
        stations.length === 0
            ? el('p', {}, el('a', { href: '#/stations' }, 'Add a station first.'))
            : el('div', { class: 'row' }, field('Station', station), field('Received at (your time)', receivedAt)),
        field('Frame (hex)', frame),
        el('div', { class: 'row gap-top' },
            el('button', { class: 'primary', type: 'submit', disabled: stations.length === 0 }, 'Add frame')));
}

function localNow() {
    const offsetMillis = new Date().getTimezoneOffset() * 60_000;
    return new Date(Date.now() - offsetMillis).toISOString().slice(0, 19);
}
