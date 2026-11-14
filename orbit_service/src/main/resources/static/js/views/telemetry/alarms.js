import { api } from '../../api.js';
import { el } from '../../dom.js';
import { dateTime, number, relative } from '../../format.js';
import { badge, card, emptyState, notify, row, showingErrors, table } from '../../ui.js';

const KIND_LABELS = { LOSS_OF_SIGNAL: 'No telemetry during a pass', LINK_QUALITY: 'Too many frames lost' };

export async function renderAlarms(container, noradId) {
    const list = card();
    container.replaceChildren(list);
    const acknowledge = alarm => notify(() => api.acknowledge(noradId, alarm.id), 'Alarm acknowledged',
        () => renderAlarms(container, noradId));
    await showingErrors(list, async () => {
        const alarms = (await api.alarms(noradId)).alarms;
        list.append(alarms.length === 0 ? emptyState('No open alarms.')
            : table(['Level', 'What', 'State', 'Raised', ''], alarms.map(alarm => alarmRow(alarm, acknowledge))));
    });
}

function alarmRow(alarm, acknowledge) {
    return row({},
        alarm.level === 'CRITICAL' ? badge('critical', '⚠ Critical') : badge('warning', '⚠ Warning'),
        [alarm.parameter ?? KIND_LABELS[alarm.kind] ?? alarm.kind, alarm.triggerValue === null ? null
            : el('div', { class: 'muted small' }, `value ${number(alarm.triggerValue, 3)}`)],
        stateLabel(alarm),
        dateTime(alarm.triggeredAt),
        alarm.state === 'ACKNOWLEDGED' ? '' : el('button', { onclick: () => acknowledge(alarm) }, 'Acknowledge'));
}

function stateLabel(alarm) {
    switch (alarm.state) {
        case 'TRIGGERED': return 'Active, not seen yet';
        case 'ACKNOWLEDGED': return 'Active, seen';
        case 'RETURNED': return `Back to normal ${relative(alarm.returnedAt)}, not seen yet`;
        default: return alarm.state;
    }
}
