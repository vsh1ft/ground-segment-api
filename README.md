# ground-segment-api

A ground-segment backend for satellite passes, tracking and telemetry, built API first. It's in an early
stage: today it keeps a list of ground stations and predicts satellite passes over them. The `orbit_service`
module downloads the latest orbital elements from [CelesTrak](https://celestrak.org/) as OMM (JSON), propagates
them with [Orekit](https://www.orekit.org/) (SGP4) and serves the passes over HTTP: when the satellite rises above
the station's minimum elevation, its highest point, and when it sets, in UTC.

## API

Interactive documentation is served by the running app at
[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html), and the OpenAPI description at
[http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs).

### Stations

```
$ curl -i -X POST http://localhost:8080/stations -H 'Content-Type: application/json' -d '{
    "name": "Vilnius", "latitudeDeg": 54.67519, "longitudeDeg": 25.23422, "altitudeM": 114,
    "minElevationDeg": 5}'
HTTP/1.1 201
Location: http://localhost:8080/stations/3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42
{"id": "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42"}
```

| Method and path | Result |
|---|---|
| `GET /stations` | all stations, by name |
| `GET /stations/{id}` | one station |
| `POST /stations` | creates a station, 201 with its `id` and `Location` |
| `PUT /stations/{id}` | replaces a station, 204 |
| `DELETE /stations/{id}` | deletes a station, 204 |

All fields are required. Names are unique (409 otherwise); latitude is -90 to 90, longitude -180 to 180 and the
minimum elevation -5 to 90 degrees.

### Passes

```
$ curl 'http://localhost:8080/passes?satellite=25544&station=3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42&duration=PT12H'
{
  "satellite": 25544,
  "station": "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42",
  "passes": [
    {
      "start": "2026-11-02T19:56:46.676Z",
      "peak": {"time": "2026-11-02T19:58:12.161Z", "elevationDeg": 6.2},
      "end": "2026-11-02T19:59:38.080Z"
    }
  ]
}
```

`satellite` is the NORAD catalog number and `station` a station ID; both are required. `duration` is an ISO-8601
duration, 1 day by default and at most 7 days.

### Errors

Errors are returned as [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details: 400 for invalid
parameters or fields, 404 for an unknown station or a satellite CelesTrak doesn't know, 409 for a duplicate station
name, 503 when CelesTrak can't be reached and nothing is stored for the satellite yet. A 400 lists every invalid
field:

```
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid request content.",
  "instance": "/stations",
  "errors": [
    {"field": "latitudeDeg", "message": "must be less than or equal to 90"},
    {"field": "name", "message": "must not be blank"}
  ]
}
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

The service listens on port 8080. Event detection and CelesTrak settings are in
`orbit_service/src/main/resources/application.yml`. Downloading orbital elements needs internet access to
reach CelesTrak.

## Tests

```
./gradlew :orbit_service:test
```

Pass predictions are checked against [Skyfield](https://rhodesmill.org/skyfield/), an independent SGP4
implementation: for a fixed ISS element set over Vilnius, rise, peak and set must agree within 1 second and the
peak elevation within 0.01 degrees (they agree within about 20 ms). The reference values come from
`tools/reference/skyfield_passes.py`:

```
python -m venv .venv && .venv/bin/pip install skyfield   # on Windows: .venv\Scripts\pip
.venv/bin/python tools/reference/skyfield_passes.py
```

## License

Apache License 2.0, see [LICENSE](LICENSE).
