import { el, number, svg, time, dateTime } from './ui.js';

const HEIGHT = 240;
const MARGIN = { top: 12, right: 16, bottom: 28, left: 64 };

function niceTicks(min, max, count = 5) {
    if (min === max) {
        const pad = Math.abs(min) * 0.1 || 1;
        min -= pad;
        max += pad;
    }
    const raw = (max - min) / count;
    const magnitude = 10 ** Math.floor(Math.log10(raw));
    const step = [1, 2, 2.5, 5, 10].map(factor => factor * magnitude).find(candidate => candidate >= raw);
    const start = Math.floor(min / step) * step;
    const ticks = [];
    for (let value = start; ticks.length === 0 || ticks.at(-1) < max; value += step) {
        ticks.push(Number(value.toPrecision(12)));
    }
    return ticks;
}

function timeLabel(milliseconds, spanMilliseconds) {
    const date = new Date(milliseconds);
    if (spanMilliseconds > 2 * 86_400_000) {
        return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
    }
    return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
}

function tableToggle(container, buildTable) {
    let table;
    const button = el('button', {
        class: 'link small', type: 'button', onclick: () => {
            if (!table) {
                table = el('div', { class: 'table-wrap' }, buildTable());
                container.append(table);
            } else {
                table.classList.toggle('hidden');
            }
            button.textContent = table.classList.contains('hidden') ? 'Show table' : 'Hide table';
        }
    }, 'Show table');
    return el('div', { class: 'chart-tools' }, button);
}

export function lineChart({ points, title, unit = '', format = value => number(value, 3), zeroLine = false }) {
    const container = el('div', { class: 'chart', role: 'figure', 'aria-label': title });
    const data = points.filter(point => typeof point.v === 'number' && Number.isFinite(point.v))
        .map(point => ({ ...point, t: new Date(point.t).getTime() }))
        .sort((a, b) => a.t - b.t);
    if (data.length === 0) {
        container.append(el('div', { class: 'empty' }, 'No numeric values to plot.'));
        return container;
    }
    const plotArea = el('div');
    container.append(plotArea, tableToggle(container, () => el('table', {},
        el('thead', {}, el('tr', {}, el('th', {}, 'Time'), el('th', { class: 'num' }, unit ? `${title} (${unit})` : title))),
        el('tbody', {}, data.slice(-500).reverse().map(point =>
            el('tr', {}, el('td', {}, dateTime(point.t)), el('td', { class: 'num' }, format(point.v))))))));
    let drawnWidth = 0;
    new ResizeObserver(() => {
        const width = Math.round(plotArea.clientWidth);
        if (width > 0 && width !== drawnWidth) {
            drawnWidth = width;
            plotArea.replaceChildren(...drawLine(data, width, unit, format, zeroLine));
        }
    }).observe(plotArea);
    return container;
}

function drawLine(data, width, unit, format, zeroLine) {
    const xMin = data[0].t;
    const xMax = data.at(-1).t === xMin ? xMin + 1 : data.at(-1).t;
    const values = data.map(point => point.v).concat(zeroLine ? [0] : []);
    const ticks = niceTicks(Math.min(...values), Math.max(...values));
    const yMin = ticks[0];
    const yMax = ticks.at(-1);
    const plotWidth = width - MARGIN.left - MARGIN.right;
    const plotHeight = HEIGHT - MARGIN.top - MARGIN.bottom;
    const x = t => MARGIN.left + ((t - xMin) / (xMax - xMin)) * plotWidth;
    const y = v => MARGIN.top + (1 - (v - yMin) / (yMax - yMin || 1)) * plotHeight;

    const chart = svg('svg', { viewBox: `0 0 ${width} ${HEIGHT}`, width, height: HEIGHT, 'aria-hidden': 'true' });
    const axis = svg('g', { class: 'axis' });
    for (const tick of ticks) {
        axis.append(svg('line', { class: 'gridline', x1: MARGIN.left, x2: width - MARGIN.right, y1: y(tick), y2: y(tick) }));
        axis.append(svg('text', { x: MARGIN.left - 8, y: y(tick) + 4, 'text-anchor': 'end' }, `${format(tick)}${unit ? ' ' + unit : ''}`));
    }
    const span = xMax - xMin;
    const labels = width < 420 ? 2 : 4;
    for (let i = 0; i <= labels; i++) {
        const t = xMin + (span * i) / labels;
        axis.append(svg('text', { x: x(t), y: HEIGHT - 8, 'text-anchor': i === 0 ? 'start' : i === labels ? 'end' : 'middle' },
            timeLabel(t, span)));
    }
    chart.append(axis);
    if (zeroLine && yMin < 0 && yMax > 0) {
        chart.append(svg('line', { class: 'zero', x1: MARGIN.left, x2: width - MARGIN.right, y1: y(0), y2: y(0) }));
    }
    if (data.length === 1) {
        chart.append(svg('circle', { class: 'marker', cx: x(data[0].t), cy: y(data[0].v), r: 4 }));
    } else {
        chart.append(svg('path', { class: 'line', d: data.map((point, i) => `${i ? 'L' : 'M'}${x(point.t).toFixed(1)},${y(point.v).toFixed(1)}`).join('') }));
    }
    const crosshair = svg('line', { class: 'crosshair hidden', y1: MARGIN.top, y2: HEIGHT - MARGIN.bottom });
    const marker = svg('circle', { class: 'marker hidden', r: 5 });
    const hit = svg('rect', { class: 'hit', x: MARGIN.left, y: 0, width: plotWidth, height: HEIGHT });
    chart.append(crosshair, marker, hit);
    const tooltip = el('div', { class: 'tooltip hidden' });

    hit.addEventListener('mousemove', event => {
        const box = chart.getBoundingClientRect();
        const t = xMin + ((event.clientX - box.left) - MARGIN.left) / plotWidth * (xMax - xMin);
        let nearest = data[0];
        for (const point of data) {
            if (Math.abs(point.t - t) < Math.abs(nearest.t - t)) {
                nearest = point;
            }
        }
        const px = x(nearest.t);
        const py = y(nearest.v);
        crosshair.setAttribute('x1', px);
        crosshair.setAttribute('x2', px);
        marker.setAttribute('cx', px);
        marker.setAttribute('cy', py);
        crosshair.classList.remove('hidden');
        marker.classList.remove('hidden');
        tooltip.replaceChildren(...[el('div', { class: 't' }, dateTime(nearest.t)),
            el('div', { class: 'v' }, `${format(nearest.v)}${unit ? ' ' + unit : ''}`),
            nearest.note ? el('div', { class: 't' }, nearest.note) : null].filter(Boolean));
        tooltip.classList.remove('hidden');
        tooltip.style.left = `${Math.min(px + 12, width - tooltip.offsetWidth - 4)}px`;
        tooltip.style.top = `${py - 10}px`;
    });
    hit.addEventListener('mouseleave', () => {
        crosshair.classList.add('hidden');
        marker.classList.add('hidden');
        tooltip.classList.add('hidden');
    });
    return [chart, tooltip];
}

function labelBeside(x, centre) {
    return x > centre ? { x: x - 8, 'text-anchor': 'end' } : { x: x + 8, 'text-anchor': 'start' };
}

export function skyPlot({ points, minElevationDeg = 0, now = Date.now() }) {
    const size = 340;
    const centre = size / 2;
    const radius = size / 2 - 28;
    const container = el('div', { class: 'chart sky', role: 'figure', 'aria-label': 'Sky plot of the pass' });
    const visible = points.filter(point => point.elevationDeg >= 0);
    const position = point => {
        const r = ((90 - point.elevationDeg) / 90) * radius;
        const angle = (point.azimuthDeg * Math.PI) / 180;
        return [centre + r * Math.sin(angle), centre - r * Math.cos(angle)];
    };
    const plot = svg('svg', { viewBox: `0 0 ${size} ${size}`, 'aria-hidden': 'true' });
    plot.append(svg('circle', { class: 'horizon', cx: centre, cy: centre, r: radius }));
    for (const elevation of [30, 60]) {
        plot.append(svg('circle', { class: 'ring', cx: centre, cy: centre, r: ((90 - elevation) / 90) * radius }));
        plot.append(svg('text', { x: centre + 3, y: centre - ((90 - elevation) / 90) * radius - 3 }, `${elevation}°`));
    }
    if (minElevationDeg > 0) {
        plot.append(svg('circle', { class: 'ring', cx: centre, cy: centre, r: ((90 - minElevationDeg) / 90) * radius,
            'stroke-dasharray': '4 4' }));
    }
    for (const [label, azimuth] of [['N', 0], ['E', 90], ['S', 180], ['W', 270]]) {
        const angle = (azimuth * Math.PI) / 180;
        plot.append(svg('line', { class: 'spoke', x1: centre, y1: centre, x2: centre + radius * Math.sin(angle), y2: centre - radius * Math.cos(angle) }));
        plot.append(svg('text', { class: 'compass', x: centre + (radius + 14) * Math.sin(angle), y: centre - (radius + 14) * Math.cos(angle) + 4, 'text-anchor': 'middle' }, label));
    }
    const chart = el('div', { class: 'chart' });
    if (visible.length > 1) {
        plot.append(svg('path', { class: 'line', d: visible.map((point, i) => `${i ? 'L' : 'M'}${position(point).map(c => c.toFixed(1)).join(',')}`).join('') }));
        const [rx, ry] = position(visible[0]);
        const [sx, sy] = position(visible.at(-1));
        plot.append(svg('circle', { class: 'marker', cx: rx, cy: ry, r: 5 }));
        plot.append(svg('text', { class: 'label', ...labelBeside(rx, centre), y: ry - 8 }, `Rise ${time(visible[0].time)}`));
        plot.append(svg('rect', { class: 'marker', x: sx - 4, y: sy - 4, width: 8, height: 8, rx: 1 }));
        plot.append(svg('text', { class: 'label', ...labelBeside(sx, centre), y: sy + 16 }, `Set ${time(visible.at(-1).time)}`));
        const current = visible.find(point => Math.abs(new Date(point.time).getTime() - now) < 30_000);
        if (current) {
            const [nx, ny] = position(current);
            plot.append(svg('circle', { class: 'now', cx: nx, cy: ny, r: 7 }));
            plot.append(svg('text', { class: 'label', x: nx + 10, y: ny + 4 }, 'Now'));
        }
    }
    const tooltip = el('div', { class: 'tooltip hidden' });
    plot.addEventListener('mousemove', event => {
        if (visible.length === 0) {
            return;
        }
        const box = plot.getBoundingClientRect();
        const scale = size / box.width;
        const mx = (event.clientX - box.left) * scale;
        const my = (event.clientY - box.top) * scale;
        let nearest = visible[0];
        let best = Infinity;
        for (const point of visible) {
            const [px, py] = position(point);
            const distance = (px - mx) ** 2 + (py - my) ** 2;
            if (distance < best) {
                best = distance;
                nearest = point;
            }
        }
        if (best > 900) {
            tooltip.classList.add('hidden');
            return;
        }
        const [px, py] = position(nearest);
        tooltip.replaceChildren(el('div', { class: 't' }, time(nearest.time)),
            el('div', { class: 'v' }, `Az ${number(nearest.azimuthDeg, 1)}° · El ${number(nearest.elevationDeg, 1)}°`));
        tooltip.classList.remove('hidden');
        tooltip.style.left = `${px / scale + 10}px`;
        tooltip.style.top = `${py / scale - 10}px`;
    });
    plot.addEventListener('mouseleave', () => tooltip.classList.add('hidden'));
    chart.append(plot, tooltip);
    container.append(chart);
    if (visible.length === 0) {
        container.append(el('div', { class: 'empty' }, 'The satellite stays below the horizon in this window.'));
    }
    container.append(tableToggle(container, () => el('table', {},
        el('thead', {}, el('tr', {}, el('th', {}, 'Time'), el('th', { class: 'num' }, 'Azimuth'), el('th', { class: 'num' }, 'Elevation'))),
        el('tbody', {}, visible.map(point => el('tr', {}, el('td', {}, time(point.time)),
            el('td', { class: 'num' }, `${number(point.azimuthDeg, 1)}°`), el('td', { class: 'num' }, `${number(point.elevationDeg, 1)}°`)))))));
    return container;
}
