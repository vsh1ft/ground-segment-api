from datetime import datetime, timedelta, timezone

from skyfield.api import EarthSatellite, load, wgs84
from skyfield.searchlib import find_discrete, find_maxima

ISS_LINE_1 = "1 25544U 98067A   26304.17644455  .00006183  00000-0  11976-3 0  9996"
ISS_LINE_2 = "2 25544  51.6332 347.1486 0007016 330.4977  29.5614 15.49124187592838"
WINDOW_START = datetime(2026, 10, 31, 4, 14, 4, tzinfo=timezone.utc)
WINDOW_LENGTH = timedelta(days=1)
VILNIUS_LATITUDE_DEG = 54.67519
VILNIUS_LONGITUDE_DEG = 25.23422
VILNIUS_ALTITUDE_M = 114.0
MIN_ELEVATION_DEG = 5.0

SECOND = 1.0 / 86400.0
RISE, CULMINATION, SET = 0, 1, 2


def main():
    ts = load.timescale()
    satellite = EarthSatellite(ISS_LINE_1, ISS_LINE_2, "ISS", ts)
    station = wgs84.latlon(VILNIUS_LATITUDE_DEG, VILNIUS_LONGITUDE_DEG, elevation_m=VILNIUS_ALTITUDE_M)
    line_of_sight = satellite - station

    def elevation_deg(t):
        return line_of_sight.at(t).altaz()[0].degrees

    def visible(t):
        return elevation_deg(t) >= MIN_ELEVATION_DEG

    elevation_deg.step_days = SECOND
    visible.step_days = SECOND

    start = ts.from_datetime(WINDOW_START)
    end = ts.from_datetime(WINDOW_START + WINDOW_LENGTH)
    times, kinds = satellite.find_events(station, start, end, altitude_degrees=MIN_ELEVATION_DEG)

    def crossing(t):
        refined, _ = find_discrete(t - 2 * SECOND, t + 2 * SECOND, visible, epsilon=1e-3 * SECOND)
        return refined[0]

    def peak(t):
        refined, elevations = find_maxima(t - 5 * SECOND, t + 5 * SECOND, elevation_deg, epsilon=1e-3 * SECOND)
        return refined[0], elevations[0]

    events = list(zip(times, kinds))
    for i in range(len(events) - 2):
        (rise, k0), (culmination, k1), (setting, k2) = events[i:i + 3]
        if (k0, k1, k2) != (RISE, CULMINATION, SET):
            continue
        peak_time, peak_elevation = peak(culmination)
        print(f"{iso(crossing(rise))}  {iso(peak_time)}  {peak_elevation:.6f}  {iso(crossing(setting))}")


def iso(t):
    return t.utc_datetime().strftime("%Y-%m-%dT%H:%M:%S.%fZ")


if __name__ == "__main__":
    main()
