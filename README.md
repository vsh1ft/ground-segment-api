# groundstation

Predicts satellite passes over a ground station. The `orbit_service` module propagates a TLE with
[Orekit](https://www.orekit.org/) (SGP4) and prints each pass: when the satellite rises above the minimum
elevation, its highest point, and when it sets, in UTC and local time.

```
5 passes over Vilnius
Pass
  begins  2026-11-02 19:56:46 UTC (2026-11-02 21:56:46 EET)
  highest 2026-11-02 19:58:12 UTC (2026-11-02 21:58:12 EET)  elevation 6.2 deg
  ends    2026-11-02 19:59:38 UTC (2026-11-02 21:59:38 EET)
...
```

## Requirements

- JDK 25. Gradle downloads it automatically if it isn't installed.
- Orekit data (Earth orientation, leap seconds). Download
  [orekit-data-main.zip](https://gitlab.orekit.org/orekit/orekit-data/-/archive/main/orekit-data-main.zip),
  unzip it into the project root and rename the folder to `orekit-data`.

## Running

```
./gradlew :orbit_service:bootRun
```

The station, satellite, forecast length, detection settings and TLEs are set in
`orbit_service/src/main/resources/application.yml`. TLEs for low orbits go stale within days, so update them
from [CelesTrak](https://celestrak.org/NORAD/elements/gp.php?CATNR=25544&FORMAT=TLE) before running.

## Tests

```
./gradlew :orbit_service:test
```
