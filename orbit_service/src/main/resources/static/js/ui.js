import { el } from './dom.js';

const TOAST_MILLIS = 4000;
const ERROR_TOAST_MILLIS = 8000;

export function toast(message, isError = false) {
    const item = el('div', { class: isError ? 'toast error' : 'toast', role: isError ? 'alert' : 'status' }, message);
    document.getElementById('toasts').append(item);
    setTimeout(() => item.remove(), isError ? ERROR_TOAST_MILLIS : TOAST_MILLIS);
}

export function errorBox(error) {
    return el('div', { class: 'error-box', role: 'alert' }, error.message ?? String(error));
}

export function emptyState(text) {
    return el('div', { class: 'empty' }, text);
}

export async function notify(action, successMessage, onSuccess = () => {}) {
    let result;
    try {
        result = await action();
    } catch (error) {
        toast(error.message, true);
        return;
    }
    toast(successMessage);
    await onSuccess(result);
}

export async function showingErrors(container, action) {
    try {
        await action();
    } catch (error) {
        container.append(errorBox(error));
    }
}

export function badge(level, text) {
    return el('span', { class: `badge ${level}` }, el('span', { class: 'dot', 'aria-hidden': 'true' }), text);
}

export function field(labelText, input) {
    return el('label', {}, labelText, input);
}

export function formValues(form) {
    return Object.fromEntries(new FormData(form).entries());
}

export function stat(value, label) {
    return el('div', { class: 'stat' }, el('div', { class: 'value' }, value), el('div', { class: 'label' }, label));
}

export function stats(...items) {
    return el('div', { class: 'stats' }, items);
}

export function card(...children) {
    return el('div', { class: 'card' }, children);
}

export function numeric(content) {
    return el('td', { class: 'num' }, content);
}

export function numericHeading(label) {
    return el('th', { class: 'num' }, label);
}

export function row(attributes, ...cells) {
    return el('tr', attributes, cells.map(cell => cell instanceof HTMLTableCellElement ? cell : el('td', {}, cell)));
}

export function table(headings, rows) {
    const head = headings.length === 0 ? null : el('thead', {}, el('tr', {},
        headings.map(heading => heading instanceof HTMLTableCellElement ? heading : el('th', {}, heading))));
    return el('div', { class: 'table-wrap' }, el('table', {}, head, el('tbody', {}, rows)));
}

export function clickable(onOpen) {
    return { class: 'clickable', tabindex: 0, onclick: onOpen, onkeydown: event => event.key === 'Enter' && onOpen() };
}
