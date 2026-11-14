import { api } from '../../api.js';
import { el } from '../../dom.js';
import { dateTime } from '../../format.js';
import { badge, card, emptyState, row, showingErrors, table } from '../../ui.js';

const PAGE_SIZE = 50;
const SEVERITY_BADGES = {
    INFO: ['neutral', 'Info'], LOW: ['warning', 'Low'], MEDIUM: ['serious', 'Medium'], HIGH: ['critical', 'High'],
};

export async function renderEvents(container, noradId) {
    const list = card();
    container.append(list);
    await showingErrors(list, async () => {
        const events = (await api.events(noradId, { limit: PAGE_SIZE })).events;
        list.append(events.length === 0 ? emptyState('No events reported.')
            : table(['Severity', 'Event', 'When'], events.map(eventRow)));
    });
}

function eventRow(event) {
    return row({},
        badge(...(SEVERITY_BADGES[event.severity] ?? ['neutral', event.severity])),
        [event.name ?? `Event ${event.eventId}`,
            event.description ? el('div', { class: 'muted small' }, event.description) : null],
        dateTime(event.generationTime));
}
