export function el(tag, attributes = {}, ...children) {
    const element = document.createElement(tag);
    for (const [key, value] of Object.entries(attributes ?? {})) {
        if (value === undefined || value === null || value === false) {
            continue;
        }
        if (key.startsWith('on') && typeof value === 'function') {
            element.addEventListener(key.slice(2), value);
        } else if (key === 'class') {
            element.className = value;
        } else if (key === 'dataset') {
            Object.assign(element.dataset, value);
        } else if (value === true) {
            element.setAttribute(key, '');
        } else {
            element.setAttribute(key, value);
        }
    }
    append(element, children);
    return element;
}

function append(element, children) {
    for (const child of children.flat(Infinity)) {
        if (child === undefined || child === null || child === false) {
            continue;
        }
        element.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
}

export function svg(tag, attributes = {}, ...children) {
    const element = document.createElementNS('http://www.w3.org/2000/svg', tag);
    for (const [key, value] of Object.entries(attributes ?? {})) {
        if (value !== undefined && value !== null) {
            element.setAttribute(key, value);
        }
    }
    for (const child of children.flat(Infinity)) {
        if (child !== undefined && child !== null && child !== false) {
            element.append(child instanceof Node ? child : document.createTextNode(String(child)));
        }
    }
    return element;
}

export function toast(message, kind = 'ok') {
    const item = el('div', { class: `toast ${kind === 'error' ? 'error' : ''}`, role: kind === 'error' ? 'alert' : 'status' },
        message);
    document.getElementById('toasts').append(item);
    setTimeout(() => item.remove(), kind === 'error' ? 8000 : 4000);
}

export function errorBox(error) {
    return el('div', { class: 'error-box', role: 'alert' }, error.message ?? String(error));
}

export function empty(text) {
    return el('div', { class: 'empty' }, text);
}

export async function guarded(action, success) {
    try {
        const result = await action();
        if (success) {
            toast(success);
        }
        return result;
    } catch (error) {
        toast(error.message, 'error');
        throw error;
    }
}

const timeFormat = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' });
const dayFormat = new Intl.DateTimeFormat(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
const dateTimeFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'medium' });

export function time(instant) {
    return timeFormat.format(new Date(instant));
}

export function dateTime(instant) {
    return instant ? dateTimeFormat.format(new Date(instant)) : '—';
}

export function day(instant) {
    const date = new Date(instant);
    const today = new Date();
    const tomorrow = new Date(today.getTime() + 86_400_000);
    if (date.toDateString() === today.toDateString()) {
        return 'Today';
    }
    if (date.toDateString() === tomorrow.toDateString()) {
        return 'Tomorrow';
    }
    return dayFormat.format(date);
}

export function duration(milliseconds) {
    const seconds = Math.round(Math.abs(milliseconds) / 1000);
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const rest = seconds % 60;
    if (hours > 0) {
        return `${hours} h ${minutes} min`;
    }
    if (minutes > 0) {
        return `${minutes} min ${String(rest).padStart(2, '0')} s`;
    }
    return `${rest} s`;
}

export function relative(instant) {
    const difference = new Date(instant).getTime() - Date.now();
    if (Math.abs(difference) < 1000) {
        return 'now';
    }
    return difference > 0 ? `in ${duration(difference)}` : `${duration(difference)} ago`;
}

export function number(value, digits = 2) {
    return typeof value === 'number' ? value.toLocaleString(undefined, { maximumFractionDigits: digits }) : value;
}

export function megahertz(hz) {
    return `${(hz / 1e6).toLocaleString(undefined, { minimumFractionDigits: 6, maximumFractionDigits: 6 })} MHz`;
}

export function badge(level, text) {
    return el('span', { class: `badge ${level}` }, el('span', { class: 'dot', 'aria-hidden': 'true' }), text);
}

export function passQuality(elevationDeg) {
    if (elevationDeg >= 45) {
        return badge('good', 'Excellent');
    }
    if (elevationDeg >= 20) {
        return badge('warning', 'Good');
    }
    return badge('neutral', 'Low');
}

export function field(labelText, input) {
    return el('label', {}, labelText, input);
}

export function formValues(form) {
    return Object.fromEntries(new FormData(form).entries());
}
