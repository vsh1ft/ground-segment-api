import { el } from '../dom.js';
import { card, field } from '../ui.js';
import { favourites } from '../state.js';
import { renderAlarms } from './telemetry/alarms.js';
import { renderEvents } from './telemetry/events.js';
import { renderFrames } from './telemetry/frames.js';
import { renderLinkReports } from './telemetry/link.js';
import { renderValues } from './telemetry/values.js';

const KNOWN = [{ noradId: 69015, name: 'FRONTIERSAT (real, from SatNOGS)' }, { noradId: 99999, name: 'Simulator' }];
const TABS = [
    { key: 'parameters', label: 'Values', render: renderValues },
    { key: 'frames', label: 'Frames', render: renderFrames },
    { key: 'alarms', label: 'Alarms', render: renderAlarms },
    { key: 'events', label: 'Events', render: renderEvents },
    { key: 'link', label: 'Link reports', render: renderLinkReports },
];

export async function renderTelemetry(main, params) {
    const noradId = Number(params.get('satellite') ?? KNOWN[0].noradId);
    const tab = TABS.find(candidate => candidate.key === params.get('tab')) ?? TABS[0];
    const select = el('select', { onchange: () => openTab(Number(select.value), tab) },
        satelliteChoices(noradId).map(choice =>
            el('option', { value: choice.noradId, selected: choice.noradId === noradId }, choice.name)));
    const content = el('div');
    main.append(el('h1', {}, 'Telemetry'),
        el('p', { class: 'lead' }, 'What the satellites sent: decoded values, the frames stations received, alarms and events.'),
        card(el('div', { class: 'row' }, field('Satellite', select))),
        el('div', { class: 'tabs', role: 'tablist' }, TABS.map(candidate => el('button', {
            class: candidate === tab ? 'active' : '', role: 'tab', 'aria-selected': String(candidate === tab),
            onclick: () => openTab(noradId, candidate)
        }, candidate.label))),
        content);
    await tab.render(content, noradId, params);
}

function satelliteChoices(noradId) {
    const isKnown = satellite => KNOWN.some(known => known.noradId === satellite.noradId);
    const choices = [...KNOWN, ...favourites().filter(satellite => !isKnown(satellite))];
    if (!choices.some(choice => choice.noradId === noradId)) {
        choices.push({ noradId, name: `Satellite ${noradId}` });
    }
    return choices;
}

function openTab(noradId, tab) {
    window.location.hash = `#/telemetry?satellite=${noradId}&tab=${tab.key}`;
}
