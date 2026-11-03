# ground-segment-api

A ground-segment backend for satellite passes, tracking and telemetry, built API first. It's in an early
stage: today it predicts satellite passes over a ground station. The `orbit_service` module downloads the latest
orbital elements from [CelesTrak](https://celestrak.org/) as OMM (JSON), propagates them with
[Orekit](https://www.orekit.org/) (SGP4) and serves the passes over HTTP: when the satellite rises above the
minimum elevation, its highest point, and when it sets, in UTC.

```
$ curl 'http://localhost:8080/passes?satellite=25544&duration=PT12H'
{
  "satellite": 25544,
  "station": "Vilnius",
  "passes": [
    {
      "start": "2026-11-02T19:56:46.676Z",
      "peak": {"time": "2026-11-02T19:58:12.161Z", "elevationDeg": 6.2},
      "end": "2026-11-02T19:59:38.080Z"
    }
  ]
}
```

`satellite` is the NORAD catalog number and is required. `duration` is an ISO-8601 duration, 1 day by default and
at most 7 days. Errors are returned as [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details: 400 for
invalid parameters, 404 for a satellite CelesTrak doesn't know, 503 when CelesTrak can't be reached and nothing is
stored for the satellite yet.

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

The service listens on port 8080. The station and the detection settings are
set in `orbit_service/src/main/resources/application.yml`. Downloading orbital elements needs internet access to
reach CelesTrak.

## Tests

```
./gradlew :orbit_service:test
```

## License

Apache License 2.0, see [LICENSE](LICENSE).
