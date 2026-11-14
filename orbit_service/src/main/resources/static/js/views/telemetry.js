import { api } from '../api.js';
import { lineChart } from '../charts.js';
import { badge, dateTime, el, empty, errorBox, field, guarded, number, relative } from '../ui.js';
import { favourites } from '../state.js';

const KNOWN = [{ noradId: 69015, name: 'FRONTIERSAT (real, from SatNOGS)' }, { noradId: 99999, name: 'Simulator' }];
const TABS = [['parameters', 'Values'], ['frames', 'Frames'], ['alarms', 'Alarms'], ['events', 'Events'], ['link', 'Link reports']];
const RANGES = [['PT6H', 'Last 6 hours', 6], ['P1D', 'Last 24 hours', 24], ['P3D', 'Last 3 days', 72], ['P7D', 'Last 7 days', 168]];

export async function renderTelemetry(main, params) {
    const noradId = Number(params.get('satellite') ?? 69015);
    const tab = TABS.some(([key]) => key === params.get('tab')) ? params.get('tab') : 'parameters';
    main.append(el('h1', {}, 'Telemetry'),
        el('p', { class: 'lead' }, 'What the satellites sent: decoded values, the frames stations received, alarms and events.'));
    const options = [...KNOWN, ...favourites().filter(item => !KNOWN.some(known => known.noradId === item.noradId))];
    if (!options.some(option => option.noradId === noradId)) {
        options.push({ noradId, name: `Satellite ${noradId}` });
    }
    const select = el('select', { onchange: () => go(Number(select.value), tab) },
        options.map(option => el('option', { value: option.noradId, selected: option.noradId === noradId }, option.name)));
    main.append(el('div', { class: 'card' }, el('div', { class: 'row' }, field('Satellite', select))),
        el('div', { class: 'tabs', role: 'tablist' }, TABS.map(([key, label]) => el('button', {
            class: key === tab ? 'active' : '', role: 'tab', 'aria-selected': String(key === tab), onclick: () => go(noradId, key)
        }, label))));
    const content = el('div');
    main.append(content);
    const views = { parameters: parameters, frames: frames, alarms: alarms, events: events, link: linkReports };
    await views[tab](content, noradId, params);
}

function go(noradId, tab) {
    window.location.hash = `#/telemetry?satellite=${noradId}&tab=${tab}`;
}

const HEADER = /^(CSP_|PACKET_|SECONDARY_HEADER|APID$|SEQUENCE_|PUS_|TIME_REFERENCE|MESSAGE_|DESTINATION_ID|SERVICE_TYPE|MESSAGE_SUBTYPE)/;
const PREFERRED = ['OBC_TEMPERATURE', 'BATTERY_VOLTAGE', 'OBC_ADC_BATTERY_VOLTAGE', 'PLATFORM_TEMPERATURE'];

async function parameters(container, noradId, params) {
    let list;
    try {
        list = (await api.parameters(noradId))
            .sort((a, b) => Number(HEADER.test(a.name)) - Number(HEADER.test(b.name)));
    } catch (error) {
        container.append(error.status === 404
            ? el('div', { class: 'card' }, empty('There is no mission database for this satellite, so its frames are kept but not decoded into values.'))
            : errorBox(error));
        return;
    }
    const filter = el('input', { type: 'search', placeholder: 'Filter, e.g. battery or temperature' });
    const range = el('select', {}, RANGES.map(([value, label]) => el('option', { value, selected: value === 'P1D' }, label)));
    const listBox = el('div', { class: 'table-wrap', style: 'max-height: 420px; overflow-y: auto' });
    const detail = el('div', { class: 'card' }, empty('Pick a value on the left.'));
    let current;
    const show = parameter => { current = parameter; values(detail, noradId, parameter, range.value); };
    range.addEventListener('change', () => current && show(current));
    const paint = () => {
        const term = filter.value.trim().toLowerCase();
        const shown = list.filter(parameter => !term || parameter.name.toLowerCase().includes(term)
            || (parameter.description ?? '').toLowerCase().includes(term));
        listBox.replaceChildren(el('table', {}, el('tbody', {}, shown.map(parameter => el('tr', {
            class: 'clickable', tabindex: 0, onclick: () => show(parameter), onkeydown: event => event.key === 'Enter' && show(parameter)
        }, el('td', {}, el('div', {}, parameter.name), parameter.description
            ? el('div', { class: 'muted small' }, parameter.description) : null),
            el('td', { class: 'muted small' }, parameter.unit ?? ''))))));
    };
    filter.addEventListener('input', paint);
    paint();
    const wanted = params.get('parameter');
    const first = list.find(parameter => parameter.name === wanted)
        ?? PREFERRED.map(name => list.find(parameter => parameter.name === name)).find(Boolean) ?? list[0];
    container.append(el('div', { class: 'grid-2' },
        el('div', { class: 'card' }, el('div', { class: 'row' }, el('div', { class: 'grow' }, field('Values', filter)), field('Period', range)),
            el('div', { style: 'margin-top: 10px' }, listBox)),
        detail));
    if (first) {
        show(first);
    }
}

async function values(container, noradId, parameter, period) {
    container.replaceChildren(el('h2', {}, parameter.name), empty('Loading…'));
    const hours = RANGES.find(([value]) => value === period)[2];
    try {
        const response = await api.values(noradId, parameter.name, {
            limit: 100, from: new Date(Date.now() - hours * 3_600_000).toISOString()
        });
        let points = response.values;
        let next = response.nextBefore;
        for (let pages = 0; next && pages < 9; pages++) {
            const more = await api.values(noradId, parameter.name, { limit: 100, before: next,
                from: new Date(Date.now() - hours * 3_600_000).toISOString() });
            points = points.concat(more.values);
            next = more.nextBefore;
        }
        container.replaceChildren(...[el('h2', {}, parameter.name),
            parameter.description ? el('p', { class: 'muted small' }, parameter.description) : null].filter(Boolean));
        if (points.length === 0) {
            container.append(empty('No values in this period.'));
            return;
        }
        const latest = points.reduce((a, b) => new Date(a.generationTime) > new Date(b.generationTime) ? a : b);
        container.append(el('div', { class: 'stats' }, el('div', { class: 'stat' },
            el('div', { class: 'value' }, format(latest.engineering), parameter.unit ? ` ${parameter.unit}` : ''),
            el('div', { class: 'label' }, `Latest, measured ${relative(latest.generationTime)}`))));
        if (parameter.kind === 'INTEGER' || parameter.kind === 'FLOAT') {
            container.append(lineChart({ title: parameter.name, unit: parameter.unit ?? '',
                points: points.map(point => ({ t: point.generationTime, v: point.engineering })) }));
        } else {
            container.append(el('div', { class: 'table-wrap' }, el('table', {},
                el('thead', {}, el('tr', {}, el('th', {}, 'Measured'), el('th', {}, 'Value'))),
                el('tbody', {}, points.slice(0, 100).map(point => el('tr', {}, el('td', {}, dateTime(point.generationTime)),
                    el('td', {}, format(point.engineering) ?? `raw ${point.raw}`)))))));
        }
        if (next) {
            container.append(el('p', { class: 'muted small' }, 'Showing the newest 1000 values of this period.'));
        }
    } catch (error) {
        container.replaceChildren(el('h2', {}, parameter.name), errorBox(error));
    }
}

function format(value) {
    if (typeof value === 'number') {
        return number(value, 3);
    }
    if (typeof value === 'string' && /^\d{4}-\d\d-\d\dT/.test(value)) {
        return dateTime(value);
    }
    return value;
}

async function frames(container, noradId) {
    const source = el('select', {}, [['', 'Everyone'], ['SATNOGS', 'SatNOGS stations'], ['OWN', 'My stations']]
        .map(([value, label]) => el('option', { value }, label)));
    const list = el('div', { class: 'card' });
    let before;
    const load = async (reset = true) => {
        if (reset) {
            before = undefined;
            list.replaceChildren();
        }
        try {
            const response = await api.frames(noradId, { limit: 25, before, source: source.value });
            if (reset && response.frames.length === 0) {
                list.append(empty('No frames received yet.'));
                return;
            }
            list.querySelector('.more')?.remove();
            list.append(el('div', { class: 'table-wrap' }, el('table', {},
                reset ? el('thead', {}, el('tr', {}, ['Received', 'Content', 'Measured', 'CRC', 'Heard by'].map(h => el('th', {}, h)))) : null,
                el('tbody', {}, response.frames.map(frame => el('tr', {},
                    el('td', {}, dateTime(frame.firstReceivedAt)),
                    el('td', {}, frame.container ?? el('span', { class: 'muted' }, 'not decoded'),
                        el('div', { class: 'mono muted' }, `${frame.raw.slice(0, 24)}… (${frame.length} bytes)`)),
                    el('td', {}, frame.generationTime ? dateTime(frame.generationTime) : '—'),
                    el('td', {}, frame.crcChecked === null ? '—' : frame.crcChecked ? badge('good', 'Checked') : badge('neutral', 'Not sent')),
                    el('td', {}, el('div', { class: 'chips' }, frame.receptions.map(reception => el('span', {
                        class: 'chip', title: `${reception.source === 'OWN' ? 'Your station' : 'SatNOGS station'}, ${dateTime(reception.receivedAt)}`
                    }, reception.source === 'OWN' ? '★ ' : '', reception.station))))))))));
            before = response.nextBefore;
            if (before) {
                list.append(el('div', { class: 'pager more' }, el('button', { onclick: () => load(false) }, 'Older frames')));
            }
        } catch (error) {
            list.append(errorBox(error));
        }
    };
    source.addEventListener('change', () => load());
    container.append(el('div', { class: 'card' }, el('div', { class: 'row' }, field('Received by', source)),
        el('p', { class: 'muted small' }, 'SatNOGS data is published under CC BY-SA 4.0 by the stations listed with each frame.')),
        list, await uploadForm(noradId, () => load()));
    await load();
}

async function uploadForm(noradId, onUploaded) {
    let stations = [];
    try {
        stations = await api.stations();
    } catch {
        stations = [];
    }
    const station = el('select', { required: true }, stations.map(item => el('option', { value: item.id }, item.name)));
    const receivedAt = el('input', { type: 'datetime-local', step: 1, required: true,
        value: new Date(Date.now() - new Date().getTimezoneOffset() * 60_000).toISOString().slice(0, 19) });
    const frame = el('textarea', { rows: 3, required: true, placeholder: 'C2A28A0021435453...' });
    return el('form', { class: 'card', onsubmit: async event => {
        event.preventDefault();
        await guarded(() => api.uploadFrame(noradId, { station: station.value, receivedAt: new Date(receivedAt.value).toISOString(),
            frame: frame.value.replace(/\s+/g, '') }), 'Frame stored');
        frame.value = '';
        onUploaded();
    } }, el('h2', {}, 'Add a frame your station received'),
        el('p', { class: 'muted small' }, 'Paste the frame in hex as your decoder (SatDump, gr-satellites…) wrote it.'),
        stations.length === 0 ? el('p', {}, el('a', { href: '#/stations' }, 'Add a station first.')) : el('div', { class: 'row' },
            field('Station', station), field('Received at (your time)', receivedAt)),
        field('Frame (hex)', frame),
        el('div', { class: 'row', style: 'margin-top: 8px' }, el('button', { class: 'primary', type: 'submit', disabled: stations.length === 0 }, 'Add frame')));
}

async function alarms(container, noradId) {
    const card = el('div', { class: 'card' });
    container.append(card);
    try {
        const list = (await api.alarms(noradId)).alarms;
        if (list.length === 0) {
            card.append(empty('No open alarms.'));
            return;
        }
        card.append(el('div', { class: 'table-wrap' }, el('table', {},
            el('thead', {}, el('tr', {}, ['Level', 'What', 'State', 'Raised', ''].map(h => el('th', {}, h)))),
            el('tbody', {}, list.map(alarm => el('tr', {},
                el('td', {}, alarm.level === 'CRITICAL' ? badge('critical', '⚠ Critical') : badge('warning', '⚠ Warning')),
                el('td', {}, alarm.parameter ?? kindLabel(alarm.kind), alarm.triggerValue !== null
                    ? el('div', { class: 'muted small' }, `value ${number(alarm.triggerValue, 3)}`) : null),
                el('td', {}, stateLabel(alarm)),
                el('td', {}, dateTime(alarm.triggeredAt)),
                el('td', {}, alarm.state === 'ACKNOWLEDGED' ? '' : el('button', { onclick: async () => {
                    await guarded(() => api.acknowledge(noradId, alarm.id), 'Alarm acknowledged');
                    container.replaceChildren();
                    await alarms(container, noradId);
                } }, 'Acknowledge'))))))));
    } catch (error) {
        card.append(errorBox(error));
    }
}

function kindLabel(kind) {
    return { LOSS_OF_SIGNAL: 'No telemetry during a pass', LINK_QUALITY: 'Too many frames lost' }[kind] ?? kind;
}

function stateLabel(alarm) {
    return {
        TRIGGERED: 'Active, not seen yet', ACKNOWLEDGED: 'Active, seen',
        RETURNED: `Back to normal ${relative(alarm.returnedAt)}, not seen yet`
    }[alarm.state] ?? alarm.state;
}

async function events(container, noradId) {
    const card = el('div', { class: 'card' });
    container.append(card);
    try {
        const list = (await api.events(noradId, { limit: 50 })).events;
        if (list.length === 0) {
            card.append(empty('No events reported.'));
            return;
        }
        const level = { INFO: ['neutral', 'Info'], LOW: ['warning', 'Low'], MEDIUM: ['serious', 'Medium'], HIGH: ['critical', 'High'] };
        card.append(el('div', { class: 'table-wrap' }, el('table', {},
            el('thead', {}, el('tr', {}, ['Severity', 'Event', 'When'].map(h => el('th', {}, h)))),
            el('tbody', {}, list.map(event => el('tr', {},
                el('td', {}, badge(...(level[event.severity] ?? ['neutral', event.severity]))),
                el('td', {}, event.name ?? `Event ${event.eventId}`, event.description
                    ? el('div', { class: 'muted small' }, event.description) : null),
                el('td', {}, dateTime(event.generationTime))))))));
    } catch (error) {
        card.append(errorBox(error));
    }
}

async function linkReports(container, noradId) {
    const card = el('div', { class: 'card' });
    container.append(card);
    try {
        const list = (await api.linkReports(noradId)).reports;
        if (list.length === 0) {
            card.append(empty('No link reports. They come from the simulator\'s live frame link, one per pass.'));
            return;
        }
        card.append(el('div', { class: 'table-wrap' }, el('table', {},
            el('thead', {}, el('tr', {}, ['Pass', 'Received', 'Lost', 'Corrupted', 'Loss'].map(h => el('th', { class: h === 'Pass' ? '' : 'num' }, h)))),
            el('tbody', {}, list.map(report => el('tr', {},
                el('td', {}, dateTime(report.passStart)),
                el('td', { class: 'num' }, report.framesReceived),
                el('td', { class: 'num' }, report.framesLost),
                el('td', { class: 'num' }, report.framesFailedCheck),
                el('td', { class: 'num' }, `${number(report.lossRatio * 100, 1)} %`)))))));
    } catch (error) {
        card.append(errorBox(error));
    }
}
