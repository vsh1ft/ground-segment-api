import { el, empty, errorBox } from './ui.js';
import { renderPass } from './views/pass.js';
import { renderPasses } from './views/passes.js';
import { renderSatellites } from './views/satellites.js';
import { renderStations } from './views/stations.js';
import { renderTelemetry } from './views/telemetry.js';

const views = {
    passes: renderPasses,
    pass: renderPass,
    satellites: renderSatellites,
    stations: renderStations,
    telemetry: renderTelemetry,
};

const navigation = { pass: 'passes' };

async function route() {
    const [path, search] = window.location.hash.replace(/^#\/?/, '').split('?');
    const name = path || 'passes';
    const main = document.getElementById('main');
    main.replaceChildren();
    window.scrollTo(0, 0);
    document.querySelectorAll('[data-nav]').forEach(link =>
        link.classList.toggle('active', link.dataset.nav === (navigation[name] ?? name)));
    const view = views[name];
    if (!view) {
        main.append(el('h1', {}, 'Not found'), empty('There is no such page.'), el('a', { href: '#/passes' }, 'Upcoming passes'));
        return;
    }
    try {
        await view(main, new URLSearchParams(search ?? ''));
    } catch (error) {
        main.append(errorBox(error));
    }
    main.focus({ preventScroll: true });
}

window.addEventListener('hashchange', route);
route();
