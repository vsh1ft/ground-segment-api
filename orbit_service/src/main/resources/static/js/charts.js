import { el, replaceChildren, svg } from './dom.js';
import { dateTime, number, time } from './format.js';

const HEIGHT = 240;
const MARGIN = { top: 12, right: 16, bottom: 28, left: 64 };
const NARROW_CHART_PX = 420;
const MAX_TABLE_ROWS = 500;
const TOOLTIP_OFFSET_PX = 12;
const MILLIS_PER_DAY = 86_400_000;

const SKY_SIZE = 340;
const SKY_CENTRE = SKY_SIZE / 2;
const SKY_RADIUS = SKY_SIZE / 2 - 28;
const SKY_HOVER_RADIUS = 30;
const NOW_TOLERANCE_MILLIS = 30_000;
const COMPASS = [['N', 0], ['E', 90], ['S', 180], ['W', 270]];

function niceTicks(min, max, count = 5) {
    if (min === max) {
        const pad = Math.abs(min) * 0.1 || 1;
        min -= pad;
        max += pad;
    }
    const raw = (max - min) / count;
    const magnitude = 10 ** Math.floor(Math.log10(raw));
    const step = [1, 2, 2.5, 5, 10].map(factor => factor * magnitude).find(candidate => candidate >= raw);
    const ticks = [];
    for (let value = Math.floor(min / step) * step; ticks.length === 0 || ticks.at(-1) < max; value += step) {
        ticks.push(Number(value.toPrecision(12)));
    }
    return ticks;
}

function timeLabel(milliseconds, spanMilliseconds) {
    const date = new Date(milliseconds);
    if (spanMilliseconds > 2 * MILLIS_PER_DAY) {
        return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
    }
    return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
}

function nearest(points, distanceTo) {
    return points.reduce((best, point) => distanceTo(point) < distanceTo(best) ? point : best);
}

function tableToggle(container, buildTable) {
    let table;
    const button = el('button', {
        class: 'link small', type: 'button', onclick: () => {
            if (table) {
                table.classList.toggle('hidden');
            } else {
                table = el('div', { class: 'table-wrap' }, buildTable());
                container.append(table);
            }
            button.textContent = table.classList.contains('hidden') ? 'Show table' : 'Hide table';
        }
    }, 'Show table');
    return el('div', { class: 'chart-tools' }, button);
}

function setHidden(elements, hidden) {
    elements.forEach(element => element.classList.toggle('hidden', hidden));
}

export function lineChart({ points, title, unit = '', format = value => number(value, 3), zeroLine = false }) {
    const container = el('div', { class: 'chart', role: 'figure', 'aria-label': title });
    const data = plottable(points);
    if (data.length === 0) {
        container.append(el('div', { class: 'empty' }, 'No numeric values to plot.'));
        return container;
    }
    const label = value => unit ? `${format(value)} ${unit}` : format(value);
    const plotArea = el('div');
    container.append(plotArea, tableToggle(container, () => el('table', {},
        el('thead', {}, el('tr', {}, el('th', {}, 'Time'), el('th', { class: 'num' }, unit ? `${title} (${unit})` : title))),
        el('tbody', {}, data.slice(-MAX_TABLE_ROWS).reverse().map(point =>
            el('tr', {}, el('td', {}, dateTime(point.t)), el('td', { class: 'num' }, format(point.v))))))));
    let drawnWidth = 0;
    new ResizeObserver(() => {
        const width = Math.round(plotArea.clientWidth);
        if (width > 0 && width !== drawnWidth) {
            drawnWidth = width;
            plotArea.replaceChildren(...drawLine(data, width, label, zeroLine));
        }
    }).observe(plotArea);
    return container;
}

function plottable(points) {
    return points.filter(point => typeof point.v === 'number' && Number.isFinite(point.v))
        .map(point => ({ ...point, t: new Date(point.t).getTime() }))
        .sort((a, b) => a.t - b.t);
}

function lineScale(data, width, zeroLine) {
    const xMin = data[0].t;
    const xMax = data.at(-1).t === xMin ? xMin + 1 : data.at(-1).t;
    const values = data.map(point => point.v).concat(zeroLine ? [0] : []);
    const ticks = niceTicks(Math.min(...values), Math.max(...values));
    const yMin = ticks[0];
    const yMax = ticks.at(-1);
    const plotWidth = width - MARGIN.left - MARGIN.right;
    const plotHeight = HEIGHT - MARGIN.top - MARGIN.bottom;
    return {
        ticks, xMin, xMax, plotWidth,
        right: width - MARGIN.right,
        crossesZero: yMin < 0 && yMax > 0,
        x: t => MARGIN.left + ((t - xMin) / (xMax - xMin)) * plotWidth,
        y: v => MARGIN.top + (1 - (v - yMin) / (yMax - yMin)) * plotHeight,
        timeAt: px => xMin + ((px - MARGIN.left) / plotWidth) * (xMax - xMin),
    };
}

function drawLine(data, width, label, zeroLine) {
    const scale = lineScale(data, width, zeroLine);
    const chart = svg('svg', { viewBox: `0 0 ${width} ${HEIGHT}`, width, height: HEIGHT, 'aria-hidden': 'true' },
        axes(scale, width, label),
        zeroLine && scale.crossesZero
            ? svg('line', { class: 'zero', x1: MARGIN.left, x2: scale.right, y1: scale.y(0), y2: scale.y(0) }) : null,
        series(data, scale));
    const tooltip = el('div', { class: 'tooltip hidden' });
    addCrosshair(chart, tooltip, data, scale, width, label);
    return [chart, tooltip];
}

function axes(scale, width, label) {
    const axis = svg('g', { class: 'axis' });
    for (const tick of scale.ticks) {
        axis.append(svg('line', { class: 'gridline', x1: MARGIN.left, x2: scale.right, y1: scale.y(tick), y2: scale.y(tick) }),
            svg('text', { x: MARGIN.left - 8, y: scale.y(tick) + 4, 'text-anchor': 'end' }, label(tick)));
    }
    const span = scale.xMax - scale.xMin;
    const last = width < NARROW_CHART_PX ? 2 : 4;
    for (let i = 0; i <= last; i++) {
        const t = scale.xMin + (span * i) / last;
        axis.append(svg('text', { x: scale.x(t), y: HEIGHT - 8, 'text-anchor': anchor(i, last) }, timeLabel(t, span)));
    }
    return axis;
}

function anchor(index, last) {
    if (index === 0) {
        return 'start';
    }
    return index === last ? 'end' : 'middle';
}

function series(data, scale) {
    if (data.length === 1) {
        return svg('circle', { class: 'marker', cx: scale.x(data[0].t), cy: scale.y(data[0].v), r: 4 });
    }
    const path = data.map((point, i) => `${i ? 'L' : 'M'}${scale.x(point.t).toFixed(1)},${scale.y(point.v).toFixed(1)}`);
    return svg('path', { class: 'line', d: path.join('') });
}

function addCrosshair(chart, tooltip, data, scale, width, label) {
    const crosshair = svg('line', { class: 'crosshair hidden', y1: MARGIN.top, y2: HEIGHT - MARGIN.bottom });
    const marker = svg('circle', { class: 'marker hidden', r: 5 });
    const hit = svg('rect', { class: 'hit', x: MARGIN.left, y: 0, width: scale.plotWidth, height: HEIGHT });
    chart.append(crosshair, marker, hit);
    hit.addEventListener('mousemove', event => {
        const t = scale.timeAt(event.clientX - chart.getBoundingClientRect().left);
        const point = nearest(data, candidate => Math.abs(candidate.t - t));
        const px = scale.x(point.t);
        const py = scale.y(point.v);
        crosshair.setAttribute('x1', px);
        crosshair.setAttribute('x2', px);
        marker.setAttribute('cx', px);
        marker.setAttribute('cy', py);
        replaceChildren(tooltip, el('div', { class: 't' }, dateTime(point.t)), el('div', { class: 'v' }, label(point.v)),
            point.note ? el('div', { class: 't' }, point.note) : null);
        setHidden([crosshair, marker, tooltip], false);
        tooltip.style.left = `${Math.min(px + TOOLTIP_OFFSET_PX, width - tooltip.offsetWidth - 4)}px`;
        tooltip.style.top = `${py - 10}px`;
    });
    hit.addEventListener('mouseleave', () => setHidden([crosshair, marker, tooltip], true));
}

export function skyPlot({ points }) {
    const container = el('div', { class: 'chart sky', role: 'figure', 'aria-label': 'Sky plot of the pass' });
    const visible = points.filter(point => point.elevationDeg >= 0);
    const plot = svg('svg', { viewBox: `0 0 ${SKY_SIZE} ${SKY_SIZE}`, 'aria-hidden': 'true' }, skyGrid(),
        visible.length > 1 ? skyPath(visible) : null);
    const tooltip = el('div', { class: 'tooltip hidden' });
    addSkyTooltip(plot, tooltip, visible);
    container.append(el('div', { class: 'chart' }, plot, tooltip));
    if (visible.length === 0) {
        container.append(el('div', { class: 'empty' }, 'The satellite stays below the horizon in this window.'));
    }
    container.append(tableToggle(container, () => el('table', {},
        el('thead', {}, el('tr', {}, el('th', {}, 'Time'), el('th', { class: 'num' }, 'Azimuth'), el('th', { class: 'num' }, 'Elevation'))),
        el('tbody', {}, visible.map(point => el('tr', {}, el('td', {}, time(point.time)),
            el('td', { class: 'num' }, `${number(point.azimuthDeg, 1)}°`), el('td', { class: 'num' }, `${number(point.elevationDeg, 1)}°`)))))));
    return container;
}

function skyRadius(elevationDeg) {
    return ((90 - elevationDeg) / 90) * SKY_RADIUS;
}

function skyPosition(azimuthDeg, radius) {
    const angle = (azimuthDeg * Math.PI) / 180;
    return [SKY_CENTRE + radius * Math.sin(angle), SKY_CENTRE - radius * Math.cos(angle)];
}

function pointPosition(point) {
    return skyPosition(point.azimuthDeg, skyRadius(point.elevationDeg));
}

function skyGrid() {
    const rings = [30, 60].flatMap(elevation => [
        svg('circle', { class: 'ring', cx: SKY_CENTRE, cy: SKY_CENTRE, r: skyRadius(elevation) }),
        svg('text', { x: SKY_CENTRE + 3, y: SKY_CENTRE - skyRadius(elevation) - 3 }, `${elevation}°`)]);
    const compass = COMPASS.flatMap(([label, azimuth]) => {
        const [edgeX, edgeY] = skyPosition(azimuth, SKY_RADIUS);
        const [labelX, labelY] = skyPosition(azimuth, SKY_RADIUS + 14);
        return [svg('line', { class: 'spoke', x1: SKY_CENTRE, y1: SKY_CENTRE, x2: edgeX, y2: edgeY }),
            svg('text', { class: 'compass', x: labelX, y: labelY + 4, 'text-anchor': 'middle' }, label)];
    });
    return [svg('circle', { class: 'horizon', cx: SKY_CENTRE, cy: SKY_CENTRE, r: SKY_RADIUS }), rings, compass];
}

function skyPath(visible) {
    const rise = visible[0];
    const set = visible.at(-1);
    const [riseX, riseY] = pointPosition(rise);
    const [setX, setY] = pointPosition(set);
    const path = visible.map((point, i) => `${i ? 'L' : 'M'}${pointPosition(point).map(c => c.toFixed(1)).join(',')}`);
    return [svg('path', { class: 'line', d: path.join('') }),
        svg('circle', { class: 'marker', cx: riseX, cy: riseY, r: 5 }),
        svg('text', { class: 'label', ...besideMarker(riseX), y: riseY - 8 }, `Rise ${time(rise.time)}`),
        svg('rect', { class: 'marker', x: setX - 4, y: setY - 4, width: 8, height: 8, rx: 1 }),
        svg('text', { class: 'label', ...besideMarker(setX), y: setY + 16 }, `Set ${time(set.time)}`),
        nowMarker(visible)];
}

function besideMarker(x) {
    return x > SKY_CENTRE ? { x: x - 8, 'text-anchor': 'end' } : { x: x + 8, 'text-anchor': 'start' };
}

function nowMarker(visible) {
    const now = Date.now();
    const current = visible.find(point => Math.abs(new Date(point.time).getTime() - now) < NOW_TOLERANCE_MILLIS);
    if (!current) {
        return null;
    }
    const [x, y] = pointPosition(current);
    return [svg('circle', { class: 'now', cx: x, cy: y, r: 7 }), svg('text', { class: 'label', x: x + 10, y: y + 4 }, 'Now')];
}

function addSkyTooltip(plot, tooltip, visible) {
    plot.addEventListener('mousemove', event => {
        if (visible.length === 0) {
            return;
        }
        const box = plot.getBoundingClientRect();
        const scale = SKY_SIZE / box.width;
        const mouseX = (event.clientX - box.left) * scale;
        const mouseY = (event.clientY - box.top) * scale;
        const distance = point => {
            const [x, y] = pointPosition(point);
            return Math.hypot(x - mouseX, y - mouseY);
        };
        const point = nearest(visible, distance);
        if (distance(point) > SKY_HOVER_RADIUS) {
            tooltip.classList.add('hidden');
            return;
        }
        const [x, y] = pointPosition(point);
        replaceChildren(tooltip, el('div', { class: 't' }, time(point.time)),
            el('div', { class: 'v' }, `Az ${number(point.azimuthDeg, 1)}° · El ${number(point.elevationDeg, 1)}°`));
        tooltip.classList.remove('hidden');
        tooltip.style.left = `${x / scale + 10}px`;
        tooltip.style.top = `${y / scale - 10}px`;
    });
    plot.addEventListener('mouseleave', () => tooltip.classList.add('hidden'));
}
