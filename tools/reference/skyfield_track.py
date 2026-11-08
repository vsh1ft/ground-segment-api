from datetime import datetime, timezone

from skyfield.api import EarthSatellite, load, wgs84

ISS_LINE_1 = "1 25544U 98067A   26304.17644455  .00006183  00000-0  11976-3 0  9996"
ISS_LINE_2 = "2 25544  51.6332 347.1486 0007016 330.4977  29.5614 15.49124187592838"
VILNIUS_LATITUDE_DEG = 54.67519
VILNIUS_LONGITUDE_DEG = 25.23422
VILNIUS_ALTITUDE_M = 114.0
TIMES = [
    datetime(2026, 11, 1, 0, 39, 0, tzinfo=timezone.utc),
    datetime(2026, 11, 1, 0, 41, 17, tzinfo=timezone.utc),
    datetime(2026, 11, 1, 0, 43, 30, tzinfo=timezone.utc),
    datetime(2026, 11, 1, 0, 45, 30, tzinfo=timezone.utc),
    datetime(2026, 11, 1, 0, 47, 30, tzinfo=timezone.utc),
    datetime(2026, 11, 1, 0, 49, 43, tzinfo=timezone.utc),
]


def main():
    ts = load.timescale()
    satellite = EarthSatellite(ISS_LINE_1, ISS_LINE_2, "ISS", ts)
    station = wgs84.latlon(VILNIUS_LATITUDE_DEG, VILNIUS_LONGITUDE_DEG, elevation_m=VILNIUS_ALTITUDE_M)
    line_of_sight = satellite - station
    for time in TIMES:
        topocentric = line_of_sight.at(ts.from_datetime(time))
        elevation, azimuth, distance, _, _, range_rate = topocentric.frame_latlon_and_rates(station)
        print(f"{time.strftime('%Y-%m-%dT%H:%M:%SZ')}  {azimuth.degrees:.6f}  {elevation.degrees:.6f}  "
              f"{distance.km:.6f}  {range_rate.km_per_s:.9f}")


if __name__ == "__main__":
    main()
