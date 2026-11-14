import { api } from '../../api.js';
import { lineChart } from '../../charts.js';
import { el, replaceChildren } from '../../dom.js';
import { dateTime, number, relative } from '../../format.js';
import { card, clickable, emptyState, errorBox, field, row, stat, stats, table } from '../../ui.js';

const HEADER_FIELD = /^(CSP_|PACKET_|SECONDARY_HEADER|APID$|SEQUENCE_|PUS_|TIME_REFERENCE|MESSAGE_|DESTINATION_ID|SERVICE_TYPE|MESSAGE_SUBTYPE)/;
const PREFERRED = ['OBC_TEMPERATURE', 'BATTERY_VOLTAGE', 'OBC_ADC_BATTERY_VOLTAGE', 'PLATFORM_TEMPERATURE'];
const PERIODS = [
    { value: 'PT6H', label: 'Last 6 hours', hours: 6 },
    { value: 'P1D', label: 'Last 24 hours', hours: 24 },
    { value: 'P3D', label: 'Last 3 days', hours: 72 },
    { value: 'P7D', label: 'Last 7 days', hours: 168 },
];
const DEFAULT_PERIOD = 'P1D';
const PAGE_SIZE = 100;
const MAX_PAGES = 10;
const MAX_TABLE_ROWS = 100;
const MILLIS_PER_HOUR = 3_600_000;
const ISO_TIME = /^\d{4}-\d\d-\d\dT/;

export async function renderValues(container, noradId, params) {
    let parameters;
    try {
        parameters = headerFieldsLast(await api.parameters(noradId));
    } catch (error) {
        container.append(error.status === 404
            ? card(emptyState('There is no mission database for this satellite, so its frames are kept but not decoded into values.'))
            : errorBox(error));
        return;
    }
    const filter = el('input', { type: 'search', placeholder: 'Filter, e.g. battery or temperature' });
    const period = el('select', {}, PERIODS.map(({ value, label }) =>
        el('option', { value, selected: value === DEFAULT_PERIOD }, label)));
    const list = el('div', { class: 'scroll-list gap-top' });
    const detail = card(emptyState('Pick a value on the left.'));
    let shown;
    const show = parameter => {
        shown = parameter;
        showHistory(detail, noradId, parameter, PERIODS.find(({ value }) => value === period.value));
    };
    const paint = () => replaceChildren(list, parameterList(matching(parameters, filter.value), show));
    period.addEventListener('change', () => shown && show(shown));
    filter.addEventListener('input', paint);
    paint();
    container.append(el('div', { class: 'grid-2' },
        card(el('div', { class: 'row' }, el('div', { class: 'grow' }, field('Values', filter)), field('Period', period)),
            list),
        detail));
    const first = firstShown(parameters, params.get('parameter'));
    if (first) {
        show(first);
    }
}

function headerFieldsLast(parameters) {
    const isHeader = parameter => Number(HEADER_FIELD.test(parameter.name));
    return parameters.sort((a, b) => isHeader(a) - isHeader(b));
}

function matching(parameters, typed) {
    const term = typed.trim().toLowerCase();
    return parameters.filter(parameter => parameter.name.toLowerCase().includes(term)
        || (parameter.description ?? '').toLowerCase().includes(term));
}

function firstShown(parameters, wanted) {
    const named = name => parameters.find(parameter => parameter.name === name);
    return named(wanted) ?? PREFERRED.map(named).find(Boolean) ?? parameters[0];
}

function parameterList(parameters, onPick) {
    return table([], parameters.map(parameter => row(clickable(() => onPick(parameter)),
        [el('div', {}, parameter.name),
            parameter.description ? el('div', { class: 'muted small' }, parameter.description) : null],
        el('td', { class: 'muted small' }, parameter.unit ?? ''))));
}

async function showHistory(container, noradId, parameter, period) {
    const heading = [el('h2', {}, parameter.name),
        parameter.description ? el('p', { class: 'muted small' }, parameter.description) : null];
    replaceChildren(container, heading, emptyState('Loading…'));
    try {
        const from = new Date(Date.now() - period.hours * MILLIS_PER_HOUR).toISOString();
        const { values, truncated } = await loadValues(noradId, parameter.name, from);
        replaceChildren(container, heading, history(parameter, values),
            truncated ? el('p', { class: 'muted small' }, `Showing the newest ${values.length} values of this period.`) : null);
    } catch (error) {
        replaceChildren(container, heading, errorBox(error));
    }
}

async function loadValues(noradId, name, from) {
    const values = [];
    let before;
    for (let page = 0; page < MAX_PAGES; page++) {
        const response = await api.values(noradId, name, { limit: PAGE_SIZE, from, before });
        values.push(...response.values);
        before = response.nextBefore;
        if (!before) {
            break;
        }
    }
    return { values, truncated: Boolean(before) };
}

function history(parameter, values) {
    if (values.length === 0) {
        return emptyState('No values in this period.');
    }
    const latest = values.reduce((a, b) => new Date(a.generationTime) > new Date(b.generationTime) ? a : b);
    const unit = parameter.unit ? ` ${parameter.unit}` : '';
    return [stats(stat([engineeringText(latest.engineering), unit], `Latest, measured ${relative(latest.generationTime)}`)),
        isNumeric(parameter) ? lineChart({
            title: parameter.name, unit: parameter.unit ?? '',
            points: values.map(value => ({ t: value.generationTime, v: value.engineering })),
        }) : table(['Measured', 'Value'], values.slice(0, MAX_TABLE_ROWS).map(value =>
            row({}, dateTime(value.generationTime), engineeringText(value.engineering) ?? `raw ${value.raw}`)))];
}

function isNumeric(parameter) {
    return parameter.kind === 'INTEGER' || parameter.kind === 'FLOAT';
}

function engineeringText(value) {
    if (typeof value === 'number') {
        return number(value, 3);
    }
    if (typeof value === 'string' && ISO_TIME.test(value)) {
        return dateTime(value);
    }
    return value;
}
