# ground-segment-api

A ground-segment backend for satellite passes, tracking and telemetry, built API first. It's in an early
stage: today it keeps a catalog of satellites and a list of ground stations, predicts passes of the catalog
satellites over the stations, and archives and decodes telemetry and events from a simulated spacecraft. The `orbit_service`
module downloads the latest orbital elements from [CelesTrak](https://celestrak.org/) as OMM (JSON), propagates
them with [Orekit](https://www.orekit.org/) (SGP4) and serves the passes over HTTP: when the satellite rises above
the station's minimum elevation, its highest point, and when it sets, in UTC.

## Web page

For using the service without calling the API by hand, the running app serves a web page at
[http://localhost:8080/](http://localhost:8080/):

- **Passes**: add your station (or use the browser's location), pick satellites, and see their upcoming passes
  together, with how high each gets and which way it crosses the sky. A pass opens a sky plot of where to point
  and the Doppler-corrected frequency to tune for each transmitter, or for a frequency you enter.
- **Satellites**: search the catalog, add a satellite from CelesTrak, upload a TLE, and see its transmitters.
- **Stations**: add, edit and remove stations.
- **Telemetry**: decoded values over time as a chart, received frames with their receptions (and uploading a frame
  your own station heard), alarms to acknowledge, events and link reports.

The page is plain HTML, CSS and JavaScript modules in `orbit_service/src/main/resources/static`: no build step and
nothing loaded from other sites. It follows the system's light or dark mode, and every chart can be shown as a
table. The chosen station and satellites are remembered in the browser.

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
| `POST /satellites/{noradId}/elements` | stores a TLE sent as `text/plain`; 201 with `Location` if it adds the satellite, 200 otherwise |
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

A satellite CelesTrak doesn't list, such as a launch from last week or the simulator's 99999, gets its orbit from
a three-line TLE. The checksums are checked, and the catalog number must match the path. The TLE is stored like a
downloaded element set, and passes use whichever stored set has the newest epoch. The refresh keeps asking CelesTrak
for such a satellite, logs "not on CelesTrak yet" without counting it as a failure, and takes over once CelesTrak
publishes it:

```
$ curl -X POST http://localhost:8080/satellites/99999/elements -H 'Content-Type: text/plain' \
       --data-binary @spacecraft_simulator/gsapi-sim.tle
{"epoch": "2026-11-08T12:00:00Z", "meanMotion": 15.2, "eccentricity": 0.0012, "inclination": 97.5, ...}
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
$ curl 'http://localhost:8080/passes?satellite=25544&station=68333219-d374-47ec-bc7d-2d004548d999&duration=PT12H'
{
  "satellite": 25544,
  "station": "68333219-d374-47ec-bc7d-2d004548d999",
  "elementsEpoch": "2026-11-07T11:10:49.950Z",
  "passes": [
    {
      "start": "2026-11-08T19:57:30.558Z", "startAzimuthDeg": 236.17, "startDirection": "SW",
      "peak": {"time": "2026-11-08T20:01:34.655Z", "elevationDeg": 36.58, "azimuthDeg": 163.18, "direction": "SSE"},
      "end": "2026-11-08T20:05:40.363Z", "endAzimuthDeg": 90.3, "endDirection": "E"
    }
  ]
}
```

`satellite` is the NORAD catalog number of a catalog satellite and `station` a station ID; both are required. `duration` is an ISO-8601
duration, 1 day by default and at most 7 days. Passes are predicted from the newest stored orbital elements;
`elementsEpoch` is their epoch. SGP4 accuracy drops the further a pass is from it, roughly 1-3 km per day in low
Earth orbit.

Each pass says where to look: the azimuth where the satellite rises, where it is highest and where it sets, in
degrees clockwise from true north, and the same direction on the 16-point compass. The example pass rises in the
southwest, crosses the southern sky at 36.6° and sets in the east.

### Tracking

```
GET /satellites/25544/track?station=3f2a9c1e-...&from=2026-11-08T21:33:53.633Z&duration=PT8M24S&step=PT1S
```

A track says where to point the antenna and which frequency to use at every step of a window: azimuth, geometric
elevation, range and range rate, below the horizon too, so a rotator can wait at the rise azimuth. Take `from` and
`duration` from a pass. Every point also gives the Doppler-corrected frequencies of the satellite's active
transmitters from the SatNOGS DB: the downlink to receive on, and the uplink to transmit on so that the satellite
hears its nominal frequency. Transponders get both edges of their bands. `downlinkHz` and `uplinkHz` replace the
transmitters with frequencies of your own, for a satellite SatNOGS doesn't list.

```
{
  "satellite": 25544, "station": "3f2a9c1e-...", "elementsEpoch": "2026-11-08T04:12:35.430048Z", "step": "PT1S",
  "frequencies": [
    {"transmitter": "...", "description": "Mode V APRS", "direction": "DOWNLINK", "nominalHz": 145825000},
    {"transmitter": "...", "description": "Mode V APRS", "direction": "UPLINK", "nominalHz": 145825000}
  ],
  "points": [
    {"time": "2026-11-08T21:33:53.633Z", "azimuthDeg": 261.92, "elevationDeg": 5.00, "rangeKm": 1870.5,
     "rangeRateKmPerS": -6.767, "frequenciesHz": [145828292, 145821708]},
    ...
  ]
}
```

That ISS pass over Vilnius (46 frequencies in the real response; two shown) starts 3.3 kHz above the APRS
frequency, passes through it at the 49.9° peak and ends 3.3 kHz below. `from` is now by default, `duration` 15
minutes (at most 1 day), `step` 1 second (100 ms to 1 hour), and a track has at most 10 000 points. Azimuth,
elevation, range and range rate are checked against Skyfield within 0.01°, 0.1 km and 0.001 km/s. The
[tracking and Doppler tutorial](docs/tutorial/tracking-and-doppler.md) explains the Doppler shift.

### Telemetry

```
GET /satellites/99999/telemetry/packets?limit=20&from=2026-11-07T22:00:00Z&apid=100&service=3&subtype=25
```

The service connects to the spacecraft simulator over TCP and archives every telemetry packet it receives: CCSDS
space packets with PUS-C secondary headers, laid out as described in the
[TM link ICD](docs/icd/tm-link.md). The packets travel in 64-byte CCSDS TM transfer frames, four per second, each
behind the `1A CF FC 1D` sync marker and randomized as on a real downlink. Housekeeping travels on virtual channel
0, event reports on virtual channel 1, and idle frames fill the gaps. The ground finds frames by the marker, drops
frames that fail their error check and searches for the next marker without reconnecting, and rebuilds packets
that span frames; after a lost frame it starts again at the next packet start. A space packet doesn't say which
spacecraft sent it, so the link is assigned a NORAD ID in the configuration (`groundstation.tmtc.link.norad-id`,
99999 for the simulator) and every packet is archived under it; frames carry spacecraft ID 42, and frames of other
spacecraft are dropped. The satellite doesn't have to be in the catalog. The
[TM frames tutorial](docs/tutorial/tm-frames.md) explains the frame layer.

Packets are listed most recently archived first; `raw` is the whole packet in hex. Every filter is optional:
`from` (inclusive) and `to` (exclusive) on the generation time, `apid`, `service` and `subtype`. For the next,
older page pass `nextBefore` as `before`; it is `null` on the last page:

```
{
  "packets": [
    {"id": 422, "apid": 100, "sequenceCount": 288, "serviceType": 3, "messageSubtype": 25,
     "generationTime": "2026-11-08T18:02:34.315826Z", "receptionTime": "2026-11-08T18:02:34.315833Z",
     "visibility": "IN_PASS", "raw": "0864C120001420031901200000818317DF50DA011D4600D501AB84"}
  ],
  "nextBefore": null
}
```

`generationTime` is the spacecraft's on-board time (TAI since 1958) converted to UTC. When the link is down the
service retries every 5 seconds.

The link also names the station it receives through (`groundstation.tmtc.link.station`, a station name). Every
packet gets a `visibility` from its reception time: `IN_PASS` during a predicted pass of the link's satellite over
that station, `OUTSIDE_PASS` otherwise, and `UNKNOWN` when no station has that name or the satellite has no orbit.
Packets are stored whatever their visibility; an `OUTSIDE_PASS` packet points to a stale TLE or a wrong station
position. Packets received before visibility existed are `UNKNOWN`.

### Link reports

```
GET /satellites/99999/link-reports?limit=20
```

The frame counters show how well the link worked. For each predicted pass the service keeps a report of the frames
received, the frames lost (gaps in the master channel frame count), how many of those failed the error check, and
the idle frames. The report of a pass in progress is written every 10 seconds and once more at the end of the pass.
In a live run with the simulator dropping 5 % and corrupting 3 % of the frames, a whole pass came out as:

```
{
  "reports": [
    {"passStart": "2026-11-08T19:09:56.490001Z", "passEnd": "2026-11-08T19:17:28.280545Z",
     "framesReceived": 1677, "framesLost": 130, "framesFailedCheck": 42, "idleFrames": 1479,
     "lossRatio": 0.07194244604316546, "updatedAt": "2026-11-08T19:17:28.280545Z"}
  ]
}
```

1677 + 130 frames in 452 seconds is the link's four frames per second. When more than 5 % of a pass's frames are
lost (`groundstation.contact.max-frame-loss`), judged once 100 frames were expected, the service raises a
`LINK_QUALITY` warning. In that run it was raised 25 seconds into the pass and returned at the predicted end of the
pass.

### Real satellites

```
GET  /satellites/69015/frames?limit=20&source=OWN&station=3f2a9c1e-...
POST /satellites/69015/frames
```

Besides the simulator, the service takes telemetry from real satellites. Every hour it imports the frames that
stations of the [SatNOGS](https://satnogs.org) network received from the satellites in
`groundstation.satnogs.import.satellites`, through the public SatNOGS Network API: no account needed. Frames your
own station received (from SatDump, gr-satellites or any decoder that writes frames) are sent with `POST`, with
your station's ID and the reception time.

The first satellite with a mission database is FrontierSat (CTS-SAT-1, NORAD 69015), a student cubesat of
CalgaryToSpace. Its extended beacon is decoded into 88 parameters (battery, temperatures, solar power, attitude,
modes), written from the team's firmware and checked field by field against a real frame decoded independently.
Values go into the same archive as the simulator's, so `/satellites/69015/parameters/OBC_TEMPERATURE/values` works
like the simulator's parameters. They are dated by the satellite's own clock: FrontierSat also sends stored beacons
hours later, and the reception time would put those in the wrong place.

The same beacon is often heard by several stations, and some stations' decoders strip the frame's CRC before
uploading. Frames are normalized (a matching CRC-32C is checked and removed) and stored once, each with the list
of stations that received it and whether that was SatNOGS or your own station:

```
{"frames": [{"id": 4712, "firstReceivedAt": "2026-11-08T19:03:34Z", "container": "BEACON_EXTENDED_V5",
             "generationTime": "2026-11-08T13:51:24.338Z", "crcChecked": true, "length": 202, "raw": "C2A28A00...",
             "receptions": [{"source": "SATNOGS", "station": "Dtec Station", "latitudeDeg": 25.12,
                             "longitudeDeg": 55.377, "altitudeM": 32.0, "observation": 15386644,
                             "receivedAt": "2026-11-08T19:03:34Z"}]}],
 "nextBefore": 4711}
```

SatNOGS data is published under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/); the stations
that received each frame are kept and listed for that reason. If CelesTrak doesn't list the satellite in a
refreshed group, add it first with `POST /satellites/69015`. The
[SatNOGS telemetry tutorial](docs/tutorial/satnogs-telemetry.md) walks through a real frame byte by byte.

### Parameters

```
GET /satellites/99999/parameters
GET /satellites/99999/parameters/BATTERY_VOLTAGE/values?limit=2&from=2026-11-08T09:00:00Z
```

Every packet is also decoded with the satellite's **mission database**, an [XTCE](https://www.omg.org/spec/XTCE/)
1.2 file ([`mdb/simulator.xml`](orbit_service/src/main/resources/mdb/simulator.xml) for the simulator), and each
value is archived with its raw value as sent and its engineering value after calibration. The first endpoint lists
what the database defines (name, description, kind, unit, enumeration labels); the second returns a parameter's
history, most recently archived first, with the same `from`/`to`, `limit` and `before` paging as packets:

```
{
  "parameter": "BATTERY_VOLTAGE",
  "unit": "V",
  "values": [
    {"id": 112, "generationTime": "2026-11-08T09:49:04.272079Z", "raw": 7409, "engineering": 7.409, "packetId": 28},
    {"id": 93, "generationTime": "2026-11-08T09:49:03.273804Z", "raw": 7409, "engineering": 7.409, "packetId": 27}
  ],
  "nextBefore": 93
}
```

`engineering` is a number for integer and float parameters and a label for enumerations (`"NOMINAL"`), or `null`
when the spacecraft sent a value the enumeration doesn't know. The header fields (`APID`, `SEQUENCE_COUNT`,
`TIME_COARSE` …) are parameters too. The mission database is validated against the official XTCE schema on startup;
a file the service can't fully understand stops the startup rather than decoding wrongly. The
[XTCE tutorial](docs/tutorial/xtce-mission-database.md) explains the format and the decoding.

### Events

```
GET /satellites/99999/events?minSeverity=LOW&limit=20
```

Events are what the spacecraft reports when something happens (PUS service 5), as opposed to the periodic
housekeeping. The simulator raises them when battery voltage or temperature cross a threshold, and its on-board
fault protection (FDIR) puts the spacecraft into SAFE mode while the battery is low. The severity comes from the
packet's PUS subtype (`INFO`, `LOW`, `MEDIUM`, `HIGH`); the name, the text and the auxiliary values come from the
mission database. Filter with `minSeverity` ("this severity or worse") and `from`/`to`, and page with `limit` and
`before` as elsewhere. Output from a live run, `minSeverity=LOW`:

```
{
  "events": [
    {"id": 6, "severity": "MEDIUM", "eventId": 3, "name": "TEMPERATURE_HIGH",
     "description": "Platform temperature above 33.0 degC", "generationTime": "2026-11-08T10:23:54.735825Z",
     "packetId": 162, "values": [{"parameter": "EVENT_TEMPERATURE", "raw": 333, "engineering": 33.300000000000004}]},
    {"id": 3, "severity": "HIGH", "eventId": 5, "name": "SAFE_MODE_ENTERED",
     "description": "FDIR switched the spacecraft to SAFE mode", "generationTime": "2026-11-08T10:22:51.736633Z",
     "packetId": 96, "values": []}
  ],
  "nextBefore": 3
}
```

An event report the service can't decode (unknown event ID, no mission database) is still listed with its
severity, just without a name: an anomaly nobody can name is the one an operator most needs to see. The
[events tutorial](docs/tutorial/pus-events.md) explains the packets and the decoding.

### Alarms

```
GET  /satellites/99999/alarms
POST /satellites/99999/alarms/3/acknowledge
```

Every received value is checked against the warning and critical limits of its type in the mission database
(XTCE `DefaultAlarm` with `StaticAlarmRanges`). For the simulator, battery voltage warns below 7.20 V, before the
on-board FDIR acts at 7.15 V, and is critical below 7.12 V; platform temperature warns above 32.0 °C and is critical
above 34.5 °C. A value out of limits raises an alarm on its parameter; the alarm keeps the most severe level reached
and stays open until the value is back in limits **and** an operator has acknowledged it (a latching alarm), so a
dip between two passes can't go unnoticed. The first endpoint lists the open alarms, most recently raised first:

```
{
  "alarms": [
    {"id": 2, "kind": "LIMIT", "parameter": "BATTERY_VOLTAGE", "level": "CRITICAL", "state": "RETURNED",
     "triggeredAt": "2026-11-08T13:42:44.097595Z", "triggerValue": 7.199,
     "returnedAt": "2026-11-08T13:43:49.097595Z", "acknowledgedAt": null, "clearedAt": null},
    {"id": 1, "kind": "LIMIT", "parameter": "PLATFORM_TEMPERATURE", "level": "CRITICAL", "state": "RETURNED",
     "triggeredAt": "2026-11-08T13:40:55.097473Z", "triggerValue": 32.1,
     "returnedAt": "2026-11-08T13:41:37.097687Z", "acknowledgedAt": null, "clearedAt": null}
  ]
}
```

In that run the battery warning came 10 seconds before the spacecraft's own `BATTERY_LOW` event. Acknowledging
alarm 2 returned it as `CLEARED` and removed it from the list; acknowledging it again is a 409.

Two alarm kinds watch the link itself: `LINK_QUALITY` when too many frames are lost (see
[Link reports](#link-reports)), and `LOSS_OF_SIGNAL` when nothing arrives. Silence on the link during a predicted
pass is an alarm too. When no packet has arrived for 10 seconds
(`groundstation.contact.silence-threshold`), measured from the start of the pass or the last packet, the service
raises a `LOSS_OF_SIGNAL` warning with no parameter and no value. It returns when packets arrive again or the pass
ends, and latches like a limit alarm. Silence between passes is normal and raises nothing.

In a live run the simulator flew the TLE over a test station, with a pass predicted from 18:02:27.7 to
18:10:07.1. The simulator started transmitting at 18:02:28.3, and every packet arrived as `IN_PASS`. Stopping it
mid-pass, after a packet at 18:02:47.3, raised a loss of signal at 18:02:57.6. Restarting it cleared the
acknowledged alarm. Stopping it again at 18:09:00 raised alarm 4, which returned exactly at the predicted end of
the pass:

```
{"id": 4, "kind": "LOSS_OF_SIGNAL", "parameter": null, "level": "WARNING", "state": "RETURNED",
 "triggeredAt": "2026-11-08T18:09:20.641008Z", "triggerValue": null,
 "returnedAt": "2026-11-08T18:10:07.106438Z", "acknowledgedAt": null, "clearedAt": null}
```

States are `TRIGGERED` (out of limits), `ACKNOWLEDGED` (out of limits, seen), `RETURNED` (back in limits, waiting
for acknowledgement) and `CLEARED` (finished). An alarm that escalates from warning to critical needs a new
acknowledgement. Alarms live in their own module, which listens to the values `tmtc` stores and runs in a nested
transaction, so a failing alarm update never loses telemetry. The
[limits and alarms tutorial](docs/tutorial/limits-and-alarms.md) explains the limits and the lifecycle, and the
[passes and loss of signal tutorial](docs/tutorial/passes-and-signal.md) explains visibility and silence.

### Errors

Errors are returned as [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details: 400 for invalid
parameters or fields or an invalid TLE, 404 for an unknown station or alarm, a satellite outside the catalog or one CelesTrak doesn't know,
409 for a duplicate station name, a satellite already in the catalog or an alarm already cleared, 503 when a satellite can't be added
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
pass ──► station, catalog ──► elements ──► shared
radio ──► catalog
tracking ──► station, elements, radio, catalog
satnogs ──► frame, catalog                              (imports SatNOGS frames)
frame ──► tmtc, station, catalog                        (decodes frames with tmtc's mission databases)
tmtc ──► contact ──► pass, station                      (tmtc uses the ccsds and xtce libraries)
alarm ──► tmtc, contact                                 (listens to stored values and to silence)
```

| Module | Responsibility |
|---|---|
| `station` | ground stations: what a station is, their storage and `/stations` |
| `pass` | pass prediction and `/passes`; Orekit propagation in the internal `pass.orekit` package |
| `catalog` | the satellite catalog, `/satellites` and the scheduled refresh from CelesTrak |
| `elements` | orbital elements: CelesTrak download, OMM storage, conversion to TLE |
| `radio` | satellite transmitters: daily sync from the SatNOGS DB and `/satellites/{noradId}/transmitters` |
| `tmtc` | telemetry: the TCP link to the spacecraft, the packet and parameter archives, the event log, the mission databases, `/satellites/{noradId}/telemetry/packets`, `/parameters` and `/events` |
| `frame` | frames received from real satellites: the frame archive with every reception, and `/satellites/{noradId}/frames` |
| `contact` | the contact plan of the TM link (its satellite's passes over its station), packet visibility, the silence watch, frame loss per pass and `/satellites/{noradId}/link-reports` |
| `tracking` | `/satellites/{noradId}/track`: azimuth, elevation, range, range rate and Doppler-corrected transmitter frequencies |
| `satnogs` | the hourly import of frames from the SatNOGS Network API |
| `alarm` | limit checking of received values, loss-of-signal alarms, the alarm lifecycle, `/satellites/{noradId}/alarms` and acknowledgement |
| `shared` | NORAD ID, error categories, the RFC 9457 error handler, OpenAPI and Orekit setup |

Three more Gradle projects sit next to `orbit_service`:

| Project | Responsibility |
|---|---|
| `ccsds` | plain-Java library (no dependencies) for CCSDS space packets, PUS-C TM headers, CUC time, the CRC, TM transfer frames, the sync marker and randomizer, and packing packets into frames and back; used by both sides of the link |
| `xtce` | plain-Java library (no dependencies) that reads an XTCE 1.2 mission database, validated against the official schema, and decodes packets into parameter values and checks them against limits |
| `spacecraft_simulator` | plain-Java program that stands in for the satellite: it sends housekeeping telemetry over TCP, and with a TLE only while it is in view of the station (Orekit) |

No two modules depend on each other. `catalog` publishes a `CatalogRefreshed` event after every refresh and `radio` listens
to it, so `catalog` doesn't know `radio` exists; in the same way `tmtc` publishes `ParameterValuesReceived` and
doesn't know `alarm` exists, and `contact` publishes `SilenceStarted` and `SilenceEnded`. `ModularityTest` fails the build on dependency cycles or on access to another module's
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

The spacecraft simulator runs as a separate process:

```
./gradlew :spacecraft_simulator:run
```

It sends a housekeeping packet every second on TCP port 10015, plus event reports when something happens; the
environment variables `SIMULATOR_TM_PORT` and `SIMULATOR_HOUSEKEEPING_PERIOD` (ISO-8601, e.g. `PT5S`) change that.
`SIMULATOR_ORBIT_PERIOD` (default `PT92M`) sets how long one orbit takes; with `PT2M` every event appears within two
minutes. The service connects to it as set in `groundstation.tmtc.link`, and either can be started first.

To fly a real orbit, give it a TLE and the station the service uses; it then transmits only while it is above the
station's minimum elevation, and takes the orbit period from the TLE:

```
SIMULATOR_TLE_FILE=spacecraft_simulator/gsapi-sim.tle \
SIMULATOR_STATION_LATITUDE=54.67519 SIMULATOR_STATION_LONGITUDE=25.23422 \
SIMULATOR_STATION_ALTITUDE=114 SIMULATOR_STATION_MIN_ELEVATION=5 \
./gradlew :spacecraft_simulator:run
```

Upload the same TLE to the service (see [Satellites](#satellites)) so that both predict the same passes.
`SIMULATOR_OREKIT_DATA` points to the Orekit data, `orekit-data` by default. `SIMULATOR_FRAME_DROP_RATE` and
`SIMULATOR_FRAME_CORRUPT_RATE` (0 to 1, 0 by default) drop or corrupt that share of the frames, to see the link
reports and the link-quality alarm at work.

## Tests

```
./gradlew test
```

A JaCoCo coverage report is written to `<project>/build/reports/jacoco/test/html/index.html` after each test run;
`./gradlew check` requires 100 % line and branch coverage in `orbit_service`, `ccsds` and `xtce`.

Requirements are listed in [`docs/requirements/`](docs/requirements), one YAML file per area (catalog, passes,
radio, interfaces, operations, ...), each requirement with an ID such as `CAT-050` and
an ECSS verification method: T (test), A (analysis), I (inspection) or R (review of design). Tests name the
requirements they verify with `@Requirement("CAT-050")`, and every test run writes a traceability matrix to
`orbit_service/build/reports/requirements/traceability.md`. `./gradlew check` fails when a requirement verified by
test has no passing test or a test names an unknown requirement.

Pass predictions are checked against [Skyfield](https://rhodesmill.org/skyfield/), an independent SGP4
implementation: for a fixed ISS element set over Vilnius, rise, peak and set must agree within 1 second and the
peak elevation within 0.01 degrees (they agree within about 20 ms). Tracks are checked the same way at six moments
of one pass: azimuth and elevation within 0.01°, range within 0.1 km, range rate within 0.001 km/s. The reference
values come from `tools/reference/skyfield_passes.py` and `tools/reference/skyfield_track.py`:

```
python -m venv .venv && .venv/bin/pip install skyfield   # on Windows: .venv\Scripts\pip
.venv/bin/python tools/reference/skyfield_passes.py
.venv/bin/python tools/reference/skyfield_track.py
```

## License

Apache License 2.0, see [LICENSE](LICENSE).
