export class ApiError extends Error {
    constructor(status, problem) {
        const fields = (problem?.errors ?? []).map(error => `${error.field}: ${error.message}`);
        super([problem?.detail ?? `Request failed (${status})`, ...fields].join(' — '));
        this.status = status;
        this.problem = problem;
    }
}

async function request(method, path, { query, json, text } = {}) {
    const url = new URL(path, window.location.origin);
    Object.entries(query ?? {})
        .filter(([, value]) => value !== undefined && value !== null && value !== '')
        .forEach(([key, value]) => url.searchParams.set(key, value));
    const headers = { Accept: 'application/json, application/problem+json' };
    let body;
    if (json !== undefined) {
        headers['Content-Type'] = 'application/json';
        body = JSON.stringify(json);
    } else if (text !== undefined) {
        headers['Content-Type'] = 'text/plain';
        body = text;
    }
    const response = await fetch(url, { method, headers, body });
    const parsed = parse(await response.text());
    if (!response.ok) {
        throw new ApiError(response.status, parsed);
    }
    return parsed;
}

function parse(content) {
    try {
        return content ? JSON.parse(content) : null;
    } catch {
        return null;
    }
}

export const api = {
    stations: () => request('GET', '/stations'),
    createStation: station => request('POST', '/stations', { json: station }),
    updateStation: (id, station) => request('PUT', `/stations/${id}`, { json: station }),
    deleteStation: id => request('DELETE', `/stations/${id}`),

    searchSatellites: (search, page = 0, size = 20) => request('GET', '/satellites', { query: { search, page, size } }),
    satellite: noradId => request('GET', `/satellites/${noradId}`),
    addSatellite: noradId => request('POST', `/satellites/${noradId}`),
    deleteSatellite: noradId => request('DELETE', `/satellites/${noradId}`),
    uploadTle: (noradId, tle) => request('POST', `/satellites/${noradId}/elements`, { text: tle }),
    transmitters: noradId => request('GET', `/satellites/${noradId}/transmitters`, { query: { status: 'ACTIVE' } }),

    passes: (satellite, station, duration) => request('GET', '/passes', { query: { satellite, station, duration } }),
    track: (noradId, query) => request('GET', `/satellites/${noradId}/track`, { query }),

    parameters: noradId => request('GET', `/satellites/${noradId}/parameters`),
    values: (noradId, name, query) =>
        request('GET', `/satellites/${noradId}/parameters/${encodeURIComponent(name)}/values`, { query }),
    frames: (noradId, query) => request('GET', `/satellites/${noradId}/frames`, { query }),
    uploadFrame: (noradId, upload) => request('POST', `/satellites/${noradId}/frames`, { json: upload }),
    events: (noradId, query) => request('GET', `/satellites/${noradId}/events`, { query }),
    alarms: noradId => request('GET', `/satellites/${noradId}/alarms`),
    acknowledge: (noradId, id) => request('POST', `/satellites/${noradId}/alarms/${id}/acknowledge`),
    linkReports: noradId => request('GET', `/satellites/${noradId}/link-reports`),
};
