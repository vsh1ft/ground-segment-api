# groundstation

Predicts satellite passes over a ground station. The `orbit_service` module downloads the latest orbital elements
from [CelesTrak](https://celestrak.org/) as OMM (JSON), propagates them with [Orekit](https://www.orekit.org/) (SGP4) and prints each pass: when the satellite rises above the minimum
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
- Docker. `bootRun` starts PostgreSQL from `compose.yaml` and leaves it running; downloaded TLEs are stored
  there, so the app keeps working offline with the last one.
- Orekit data (Earth orientation, leap seconds). Download
  [orekit-data-main.zip](https://gitlab.orekit.org/orekit/orekit-data/-/archive/main/orekit-data-main.zip),
  unzip it into the project root and rename the folder to `orekit-data`.

## Running

```
./gradlew :orbit_service:bootRun
```

The station, satellite, forecast length and detection settings are set in
`orbit_service/src/main/resources/application.yml`. Running it needs internet access to reach CelesTrak.

## Tests

```
./gradlew :orbit_service:test
```

## License

Apache License 2.0, see [LICENSE](LICENSE).
