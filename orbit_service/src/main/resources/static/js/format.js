const MILLIS_PER_DAY = 86_400_000;

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
    const tomorrow = new Date(today.getTime() + MILLIS_PER_DAY);
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
