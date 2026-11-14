const STATION = 'groundsegment.station';
const FAVOURITES = 'groundsegment.favourites';

export function selectedStation() {
    return localStorage.getItem(STATION);
}

export function selectStation(id) {
    localStorage.setItem(STATION, id);
}

export function favourites() {
    try {
        return JSON.parse(localStorage.getItem(FAVOURITES)) ?? [];
    } catch {
        return [];
    }
}

export function addFavourite(satellite) {
    const list = favourites().filter(item => item.noradId !== satellite.noradId);
    list.push({ noradId: satellite.noradId, name: satellite.name });
    localStorage.setItem(FAVOURITES, JSON.stringify(list));
}

export function removeFavourite(noradId) {
    localStorage.setItem(FAVOURITES, JSON.stringify(favourites().filter(item => item.noradId !== noradId)));
}

export function isFavourite(noradId) {
    return favourites().some(item => item.noradId === noradId);
}
