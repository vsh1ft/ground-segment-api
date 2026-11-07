# ground-segment-api

A ground-segment backend for satellite passes, tracking and telemetry, built API first. It's in an early
stage: today it keeps a catalog of satellites and a list of ground stations, and predicts passes of the catalog
satellites over the stations. The `orbit_service`
module downloads the latest orbital elements from [CelesTrak](https://celestrak.org/) as OMM (JSON), propagates
them with [Orekit](https://www.orekit.org/) (SGP4) and serves the passes over HTTP: when the satellite rises above
the station's minimum elevation, its highest point, and when it sets, in UTC.

## API

Interactive documentation is served by the running app at
[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html), and the OpenAPI description at
[http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs).

### Satellites

Passes are predicted only for satellites in the catalog. A background job fills and refreshes it from CelesTrak:
every 4 hours it imports the `amateur`, `weather` and `stations` groups (about 190 satellites) and downloads new
orbital elements for every other catalog satellite. After a restart it continues the schedule from the last
download, so restarting doesn't download again early; with an empty database it runs right away. Name and international
designator follow CelesTrak, so a renamed satellite is found by its new name. Any other satellite can be added by
its NORAD catalog number; a deleted group satellite comes back at the next refresh.

```
$ curl -i -X POST http://localhost:8080/satellites/25544
HTTP/1.1 201
Location: http://localhost:8080/satellites/25544

$ curl 'http://localhost:8080/satellites?search=iss'
{
  "content": [
    {"noradId": 25544, "name": "ISS (ZARYA)", "internationalDesignator": "1998-067A",
     "addedAt": "2026-11-04T10:15:30Z", "latestEpoch": "2026-11-04T06:12:45.123Z"}
  ],
  "page": {"size": 20, "number": 0, "totalElements": 1, "totalPages": 1}
}
```

| Method and path | Result |
|---|---|
| `GET /satellites?search=&page=&size=` | one page of catalog satellites, by name |
| `GET /satellites/{noradId}` | one satellite |
| `GET /satellites/{noradId}/elements?page=&size=` | one page of the satellite's stored orbital elements, newest epoch first |
| `POST /satellites/{noradId}` | adds a satellite from CelesTrak, 201 with `Location` |
| `DELETE /satellites/{noradId}` | removes a satellite and its stored elements, 204 |

`search` matches part of the name, ignoring case, or a NORAD catalog number exactly. `page` starts at 0; `size` is
20 by default and at most 100.

Every element set the refresh downloads is kept, one per epoch, so `/elements` shows how the orbit was updated over
time. Each entry has the SGP4 mean elements (`meanMotion` in revolutions per day, angles in degrees, `bstar`,
`meanMotionDot`, `meanMotionDdot`), `elementSetNo`, `revAtEpoch`, and when it was last downloaded:

```
$ curl 'http://localhost:8080/satellites/25544/elements?size=1'
{
  "content": [
    {"epoch": "2026-11-01T18:56:25.712736Z", "meanMotion": 15.49143202, "eccentricity": 0.0007095,
     "inclination": 51.6332, "raOfAscNode": 339.1679, "argOfPericenter": 336.8847, "meanAnomaly": 23.1822,
     "bstar": 0.00011503, "meanMotionDot": 5.925e-5, "meanMotionDdot": 0, "elementSetNo": 999,
     "revAtEpoch": 59308, "downloadedAt": "2026-11-04T10:00:00Z"}
  ],
  "page": {"size": 1, "number": 0, "totalElements": 12, "totalPages": 12}
}
```

### Transmitters

Radio transmitters of catalog satellites come from the [SatNOGS DB](https://db.satnogs.org): downlink and uplink
frequencies in Hz, modulation and baud rate, so a radio amateur knows what to tune to. They are synced once a day,
right after a catalog refresh, and mirror SatNOGS: entries SatNOGS removes are removed here too.

```
$ curl 'http://localhost:8080/satellites/25544/transmitters?status=ACTIVE'
[
  {"uuid": "ZJxCeQmih9zDfYNVrB4wRN", "description": "Mode V APRS", "type": "TRANSCEIVER", "status": "ACTIVE",
   "uplinkLowHz": 145825000, "uplinkHighHz": null, "downlinkLowHz": 145825000, "downlinkHighHz": null,
   "mode": "AFSK", "uplinkMode": "AFSK", "baud": 1200.0, "invert": false,
   "updatedAt": "2019-06-16T08:49:33.586159Z"},
  ...
]
```

| Method and path | Result |
|---|---|
| `GET /satellites/{noradId}/transmitters?status=` | the satellite's transmitters, by downlink frequency |

`status` is `ACTIVE` or `INACTIVE`; without it every transmitter is listed. `type` is `TRANSMITTER` (downlink
only), `TRANSCEIVER` (uplink and downlink) or `TRANSPONDER` (a band relayed from `uplinkLowHz`-`uplinkHighHz` to
`downlinkLowHz`-`downlinkHighHz`, mirrored when `invert` is true).

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
  "elementsEpoch": "2026-11-02T06:12:45.123Z",
  "passes": [
    {
      "start": "2026-11-02T19:56:46.676Z",
      "peak": {"time": "2026-11-02T19:58:12.161Z", "elevationDeg": 6.2},
      "end": "2026-11-02T19:59:38.080Z"
    }
  ]
}
```

`satellite` is the NORAD catalog number of a catalog satellite and `station` a station ID; both are required. `duration` is an ISO-8601
duration, 1 day by default and at most 7 days. Passes are predicted from the newest stored orbital elements;
`elementsEpoch` is their epoch. SGP4 accuracy drops the further a pass is from it, roughly 1-3 km per day in low
Earth orbit.

### Errors

Errors are returned as [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details: 400 for invalid
parameters or fields, 404 for an unknown station, a satellite outside the catalog or one CelesTrak doesn't know,
409 for a duplicate station name or a satellite already in the catalog, 503 when a satellite can't be added
because CelesTrak can't be reached. A 400 lists every invalid
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

## Architecture

`orbit_service` is a modular monolith built with [Spring Modulith](https://spring.io/projects/spring-modulith).
Each top-level package under `lt.vsh1ft.groundstation` is a module; its sub-packages are internal to it.

```
station ──► pass ──► catalog ──► elements ──► shared
              └─────────────────────►┘
radio ─────────────► catalog
```

| Module | Responsibility |
|---|---|
| `station` | ground stations: storage and `/stations` |
| `pass` | pass prediction and `/passes`; Orekit propagation in the internal `pass.orekit` package |
| `catalog` | the satellite catalog, `/satellites` and the scheduled refresh from CelesTrak |
| `elements` | orbital elements: CelesTrak download, OMM storage, conversion to TLE |
| `radio` | satellite transmitters: daily sync from the SatNOGS DB and `/satellites/{noradId}/transmitters` |
| `shared` | NORAD ID, error categories, the RFC 9457 error handler, OpenAPI and Orekit setup |

`pass` finds stations through its `GroundStationSource` interface, which `station` implements, so no two modules
depend on each other. `catalog` publishes a `CatalogRefreshed` event after every refresh and `radio` listens
to it, so `catalog` doesn't know `radio` exists. `ModularityTest` fails the build on dependency cycles or on access to another module's
internals, and writes PlantUML module diagrams to `build/spring-modulith-docs`. The running app describes its
modules at [http://localhost:8080/actuator/modulith](http://localhost:8080/actuator/modulith).

## Requirements

- JDK 25. Gradle downloads it automatically if it isn't installed.
- Docker. `bootRun` starts PostgreSQL from `compose.yaml` and leaves it running; downloaded orbital elements are
  stored there, so passes are still predicted while CelesTrak can't be reached.
- Orekit data (Earth orientation, leap seconds). Download
  [orekit-data-main.zip](https://gitlab.orekit.org/orekit/orekit-data/-/archive/main/orekit-data-main.zip),
  unzip it into the project root and rename the folder to `orekit-data`.

## Running

```
./gradlew :orbit_service:bootRun
```

The service listens on port 8080. Event detection, catalog refresh (`groundstation.catalog.refresh`: interval, at least 2 h, and
groups), transmitter sync (`groundstation.radio.sync.interval`), CelesTrak and SatNOGS settings are in
`orbit_service/src/main/resources/application.yml`. Downloading orbital elements and transmitters needs internet
access to reach CelesTrak and the SatNOGS DB.

## Tests

```
./gradlew :orbit_service:test
```

A JaCoCo coverage report is written to `orbit_service/build/reports/jacoco/test/html/index.html` after each test run.

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
