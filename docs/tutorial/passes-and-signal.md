# Tutorial: passes and loss of signal

This tutorial follows [the limits and alarms tutorial](limits-and-alarms.md). The ground can already predict passes
and receive telemetry, but it treats the two as unrelated: telemetry is accepted at any time, and nobody notices
when it stops. Here you will see why a ground segment ties them together, how a TLE gets into the catalog when
CelesTrak doesn't have it, how each packet is classified as inside or outside a pass, and how silence during a pass
becomes an alarm.

There is no code to write. The last part maps everything to the planned code.

---

## Part 0: why the ground cares about passes

A low Earth orbit satellite circles the Earth in about 95 minutes. A given station sees it only while it is above
the horizon, a few times a day, for 5 to 15 minutes each time. That window is a **pass**, also called a **contact**.

```
 elevation
   90° ┤
       │                 peak
   45° ┤              .-'''''-.
       │           .-'         '-.
   10° ┤ - - - - -/- - - - - - - -\- - - - -   station minimum elevation
    0° ┼────────AOS───────────────LOS─────────► time
              acquisition      loss
              of signal        of signal
```

- **AOS** (acquisition of signal): the satellite rises above the station's minimum elevation.
- **LOS** (loss of signal): it sets below it again.
- The **minimum elevation** is set per station. Below it, terrain, buildings and the long path through the
  atmosphere make the link unusable.

Knowing the passes gives the ground two expectations:

| Expectation | If it is broken | Usual causes |
|---|---|---|
| no telemetry **outside** a pass | packet arrives with nothing overhead | stale TLE, wrong station position, wrong satellite ID on the link |
| telemetry **during** a pass | silence while the satellite is in view | antenna not tracking, receiver or link down, spacecraft transmitter off or in trouble |

The first is a **data quality** question: the packet is still real data, but something in the ground's model is
off. The second is an **operations** question: someone should look now, while the pass lasts.

---

## Part 1: one orbit, two programs

Until now the simulator had no orbit. It sent telemetry every second, all day. To make passes testable, the
simulator now **flies a TLE**: it propagates the same element set with Orekit's SGP4, computes its elevation as seen
from one station, and transmits only while it is above that station's minimum elevation.

```
            same TLE                            same station
   ┌──────────────────────┐            ┌──────────────────────────┐
   │ spacecraft simulator │            │       ground service      │
   │  SGP4 → elevation     │  packets  │  SGP4 → predicted passes  │
   │  transmit if ≥ 10°    │ ────────► │  classify every packet    │
   └──────────────────────┘            └──────────────────────────┘
```

Because both sides use the same elements, the same propagator and the same station, the simulator's real
visibility and the ground's predicted passes agree to within the detection tolerance. That is deliberate: it makes
every mismatch the ground reports in a test a ground bug, not orbit noise.

A real mission is less tidy:

- The **TLE is only a model**. A real satellite drifts from its TLE by roughly a kilometre a day, more after a
  manoeuvre or a few days after launch. Passes then start a few seconds early or late.
- **Signal at low elevation is weak**. A real station often hears the first packet some seconds after AOS and loses
  the last one some seconds before LOS.
- The spacecraft keeps **working between passes**. Events raised out of view are lost in this simulator, because it
  has no on-board storage. Real spacecraft record telemetry and dump it in the next pass, which is a later topic.

The simulator still works without a TLE: if none is configured, it transmits all the time, as before.

### The simulator's configuration

| Environment variable | Example | Meaning |
|---|---|---|
| `SIMULATOR_TLE_FILE` | `spacecraft_simulator/gsapi-sim.tle` | the element set to fly; unset means always transmitting |
| `SIMULATOR_STATION_LATITUDE` | `54.6872` | degrees, north positive |
| `SIMULATOR_STATION_LONGITUDE` | `25.2797` | degrees, east positive |
| `SIMULATOR_STATION_ALTITUDE` | `112` | metres above the WGS84 ellipsoid |
| `SIMULATOR_STATION_MIN_ELEVATION` | `10` | degrees |
| `SIMULATOR_OREKIT_DATA` | `orekit-data` | Orekit's Earth orientation and time data |

With a TLE, the orbit period used for sunlight and eclipse comes from the TLE's mean motion, so the battery cycle
matches the real orbit. Without one, `SIMULATOR_ORBIT_PERIOD` is still used.

---

## Part 2: getting a TLE into the catalog

The catalog downloads elements from CelesTrak. Satellite 99999 doesn't exist there, and neither does a cubesat
launched yesterday: CelesTrak usually publishes elements for new objects only after a few days. Operators and
radio amateurs then use TLEs from the launch provider or the satellite team. A new endpoint accepts one:

```
POST /satellites/99999/elements
Content-Type: text/plain

GSAPI-SIM
1 99999U 26999A   26312.50000000  .00000000  00000+0  00000+0 0  9997
2 99999  97.5000 312.0000 0012000  90.0000 270.0000 15.20000000    14
```

### Reading a TLE

A TLE is three lines: a name, and two lines of exactly 69 characters each, with every field in fixed columns.

| Line | Columns | Value above | Meaning |
|---|---|---|---|
| 1 | 3–7 | `99999` | NORAD catalog number |
| 1 | 10–17 | `26999A` | international designator: launch year 2026, launch 999, piece A |
| 1 | 19–32 | `26312.50000000` | epoch: day 312.5 of 2026, which is 8 November 2026 12:00 UTC |
| 1 | 54–61 | `00000+0` | B\*, the drag term, here 0 |
| 2 | 9–16 | `97.5000` | inclination, degrees (97.5° is a typical Sun-synchronous orbit) |
| 2 | 18–25 | `312.0000` | right ascension of the ascending node, degrees |
| 2 | 27–33 | `0012000` | eccentricity with an implied leading point: 0.0012 |
| 2 | 35–42 | `90.0000` | argument of perigee, degrees |
| 2 | 44–51 | `270.0000` | mean anomaly at epoch, degrees |
| 2 | 53–63 | `15.20000000` | mean motion, revolutions per day: a period of 94.7 minutes |
| both | 69 | `7`, `4` | checksum: the digits of the line summed, each `-` counting as 1, modulo 10 |

The checksum catches a TLE that was typed wrong or truncated by an email client, and the endpoint rejects a TLE
whose checksums don't match. It also rejects a TLE whose catalog number isn't the one in the path, because that is
almost always a copy-paste mistake. The TLE is stored as the same element set row a CelesTrak download produces,
so passes, the element history and everything else work unchanged.

| Situation | Response |
|---|---|
| satellite not in the catalog yet | added with the TLE's name; `201 Created` with the element set |
| satellite already in the catalog | element set added; `200 OK` |
| same epoch already stored | nothing new; `200 OK` |
| bad checksum, wrong line length, unparsable field | `400` naming the problem |
| catalog number differs from the path | `400` |

For predictions the **newest epoch wins**, whichever source it came from. That is the existing rule, and it means an
operator's own fresher orbit solution replaces an older CelesTrak element set, and a fresher CelesTrak set replaces
an older upload.

### The catalog refresh

Every 4 hours the refresh downloads elements for every catalog satellite. For 99999, or for a new launch CelesTrak
doesn't list yet, CelesTrak answers "not found". The refresh still asks every time, but logs that answer at INFO as
"not on CelesTrak yet" and doesn't count it as a failure. Timeouts and server errors stay warnings. When CelesTrak
starts listing a new launch, the refresh picks it up without anyone changing anything.

---

## Part 3: which station is the link?

Pass predictions need a station, and the TM link must say which one it receives through. It names it:

```yaml
groundstation:
  tmtc:
    link:
      norad-id: 99999
      station: Vilnius
```

Stations live in the database and are managed through `/stations`. The name is looked up when it is needed, not
once at startup, so a station can be created after the service starts, and editing its position or minimum
elevation takes effect at the next plan update. If no station has that name, the ground can't predict passes and
classifies packets as `UNKNOWN` (Part 4) until one is created.

The simulator gets the same position through its environment variables. They are two copies of one fact, and a
mismatch between them is exactly what the classification in Part 4 detects.

---

## Part 4: classifying every packet

Every received packet gets a **visibility**:

| Visibility | Meaning |
|---|---|
| `IN_PASS` | received during a predicted pass |
| `OUTSIDE_PASS` | received when no pass was predicted |
| `UNKNOWN` | no prediction possible: no station with the configured name, or no orbit data for the satellite |

The packet is **always stored**, whatever its visibility. Telemetry is never thrown away because a model disagrees
with it. The visibility is stored with the packet and returned by the telemetry API:

```json
{"id": 4512, "apid": 100, "sequenceCount": 811, "serviceType": 3, "messageSubtype": 25,
 "generationTime": "2026-11-08T09:12:03Z", "receptionTime": "2026-11-08T09:12:03.041Z",
 "visibility": "IN_PASS"}
```

### Reception time, not generation time

Visibility is a property of the **link**, so it is judged by the **reception time** from the ground clock. The
generation time is when the spacecraft produced the data. For live housekeeping the two are milliseconds apart, but
recorded telemetry dumped in a pass can be hours old. Judging it by generation time would mark perfectly normal
dumped data as `OUTSIDE_PASS`.

### The contact plan

Running SGP4 and event detection for every packet would be wasteful, since packets arrive every second and passes
change slowly. The ground keeps a **contact plan**: the passes of the link's satellite over the link's station from
one hour ago until 24 hours ahead. Each packet is checked against that list.

```
contact plan for 99999 over Vilnius, made at 09:00
  08:21:40 – 08:31:05
  09:55:12 – 10:06:48
  11:31:03 – 11:39:20
  ...
```

The plan is remade every 10 minutes, so a new TLE or an edited station takes effect within 10 minutes. It starts
one hour in the past because a pass already in progress has no acquisition event inside a window that starts
mid-pass, and the predictor would miss it.

Existing packets in the archive keep visibility `UNKNOWN`: nothing predicted passes when they arrived, and
history is not reclassified.

---

## Part 5: noticing silence

The second expectation needs a **timer**, not a packet, because the problem is a packet that doesn't arrive. A
**contact watch** looks at the link once a second:

```
during a pass [AOS, LOS):
    silent since = the later of (AOS, the last packet's reception time)
    silent for   = now − silent since
    if silent for ≥ threshold         → loss of signal
    if a packet arrives               → signal back
outside a pass:
    silence is normal                 → nothing to watch
    a loss of signal still open at LOS → it ends there
```

The threshold is `groundstation.contact.silence-threshold`, by default 10 seconds. With housekeeping every second,
10 seconds is ten missing packets, long enough to ignore a short drop and short enough to leave most of a pass for
the operator to react.

Measuring from AOS gives the link a grace period too: a pass starts as silent, and only after 10 seconds with no
packet at all is it a loss of signal. In reality the first packets of a pass often arrive a little after AOS, and
this grace absorbs that.

What counts is only whether packets arrive. A link that is down (TCP connection refused), a simulator that is
stopped and a spacecraft that has stopped transmitting all look the same from the ground: silence.

### The loss-of-signal alarm

Loss of signal is an **alarm**, handled by the `alarm` module like a limit alarm, with the same latching lifecycle.
Alarms now have a **kind**:

| Kind | Raised when | `parameter` | `triggerValue` | Times come from |
|---|---|---|---|---|
| `LIMIT` | a value goes outside its limits | the parameter | the value | the packet's generation time |
| `LOSS_OF_SIGNAL` | silence reaches the threshold during a pass | `null` | `null` | the ground clock |

A loss-of-signal alarm is always a `WARNING`. Missing telemetry is a reason to look, but the ground doesn't know that
anything on board is wrong. `CRITICAL` stays for values that are known to be dangerous.

The lifecycle maps onto the existing states:

| Event | Alarm |
|---|---|
| silence reaches the threshold | raised as `TRIGGERED`, `WARNING` |
| operator acknowledges | `ACKNOWLEDGED` |
| a packet arrives | `RETURNED`, or `CLEARED` if it was acknowledged |
| the pass ends while still silent | `RETURNED`, or `CLEARED` if it was acknowledged: silence is no longer unexpected |
| silence again in a later pass, alarm still `RETURNED` | `TRIGGERED` again: the same alarm, still waiting for an operator |

There is one open alarm per satellite, kind and parameter. For `LOSS_OF_SIGNAL` the parameter is always `null`, so
there is at most one open loss-of-signal alarm per satellite. The unique index uses `NULLS NOT DISTINCT`, because
Postgres otherwise treats every `null` as different and would allow any number of them.

### Who knows what

```
 tmtc ─────────► contact ─────────► pass, station, elements
   │                 │ silence started / ended (events)
   ▼                 ▼
 alarm ◄─────────────┘
```

- `tmtc` tells `contact` about every received packet and gets the visibility back.
- `contact` owns the contact plan and the watch. It publishes "silence started" and "silence ended" and knows nothing
  about alarms.
- `alarm` listens to `contact`, as it already listens to `tmtc`.

No module depends on one that depends on it, which Spring Modulith checks in `ModularityTest`.

The watch keeps its state in memory. After a restart it starts with no last packet, so silence is measured from
the later of AOS and the start of the service. An open alarm in the database is simply updated by the next
events.

---

## Part 6: a pass, step by step

The simulator transmits housekeeping every second while it is above 10°. The pass is predicted from 09:55:12 to
10:06:48. In the middle of the pass, the simulator is stopped for a minute.

| Time | What happens | Visibility | Loss-of-signal alarm |
|---|---|---|---|
| 09:40:00 | nothing received, no pass | | none |
| 09:55:12 | AOS: the simulator starts transmitting | | none |
| 09:55:12.04 | first housekeeping packet | `IN_PASS` | none |
| 10:00:30 | last packet before the simulator is stopped | `IN_PASS` | none |
| 10:00:40 | 10 s without a packet | | #7 `TRIGGERED`, `WARNING` |
| 10:01:05 | operator acknowledges #7 | | #7 `ACKNOWLEDGED` |
| 10:01:31 | simulator restarted, a packet arrives | `IN_PASS` | #7 `CLEARED` |
| 10:06:48 | LOS: the simulator stops transmitting | | none |
| 10:06:49 | nothing: silence after LOS is normal | | none |

Had the operator not acknowledged, #7 would be `RETURNED` at 10:01:31 and stay in the alarm list until acknowledged.

Now suppose the station in the service's database is 50 km from the position the simulator uses. The two disagree
about AOS and LOS by some seconds, and the first or last packets of each pass arrive as `OUTSIDE_PASS`. That is the
data quality signal from Part 0: the packets are fine, the configuration is not.

---

## Part 7: how this maps to the code

| Piece | Code |
|---|---|
| simulator | reads a TLE file and a station from environment variables; Orekit SGP4 elevation decides whether each step's packets are sent; the orbit period comes from the mean motion; without a TLE it transmits all the time |
| TLE upload | `POST /satellites/{noradId}/elements` with `text/plain`; Orekit parses and checks the TLE; it is converted to the same `omm` row as a CelesTrak download; `201` for a new satellite, `200` otherwise, `400` for a bad TLE or a mismatched number |
| catalog refresh | `SatelliteNotFoundException` from CelesTrak is logged at INFO and not counted as a failure |
| link station | `groundstation.tmtc.link.station`, a station name looked up through `GroundStationSource`, which gains a lookup by name |
| module boundary | a new Spring Modulith module `contact`: `tmtc` calls it for every packet, `contact` publishes silence events, `alarm` listens |
| contact plan | passes from one hour ago to 24 hours ahead from the existing `PassPredictor`, remade every 10 minutes |
| visibility | `IN_PASS`, `OUTSIDE_PASS` or `UNKNOWN` by reception time; a new `visibility` column on `tm_packet` and field in the telemetry API |
| contact watch | runs every second; silence threshold `groundstation.contact.silence-threshold`, default 10 s |
| alarms | new `kind` (`LIMIT`, `LOSS_OF_SIGNAL`); `parameter` and `triggerValue` become nullable; the unique index covers satellite, kind and parameter with `NULLS NOT DISTINCT` |
| documents | requirements in `docs/requirements/contact.yml`, additions to the alarm, catalog and telemetry requirements, README section |

Recorded telemetry and its dumps, passes for more than one station, a contact plan exposed through the API, and
filtering telemetry by visibility are left for later rounds.

---

## Exercises

1. A packet with generation time 03:10:00 is received at 09:58:00, during a pass. What is its visibility, and why
   isn't it `OUTSIDE_PASS`?
2. A pass runs from 14:00:00 to 14:09:00. The first packet arrives at 14:00:08 and the next ones every second until
   14:04:00. Nothing arrives after that. When is the loss-of-signal alarm raised, and what is its state at 14:09:00
   if nobody acknowledges it?
3. Why would it be wrong to raise the loss-of-signal alarm at 14:09:11 in exercise 2, ten seconds after LOS?
4. The TLE upload contains `1 25544U ...` but is sent to `/satellites/99999/elements`. Why reject it instead of storing
   it under 25544?
5. You upload a TLE for a new launch, 61234. A week later CelesTrak starts listing 61234 with a newer epoch. Which
   element set do pass predictions use, and did you have to do anything?
6. The service's station is called `Vilnius`, and someone renames it to `Vilnius-1`. What happens to packet
   visibility and to loss-of-signal alarms?

<details>
<summary>Answers</summary>

1. `IN_PASS`. Visibility is judged by the reception time, 09:58:00, which is inside the pass. The generation time
   only says the data was recorded on board hours earlier.
2. At 14:04:10, ten seconds after the last packet. At 14:09:00 the pass ends while still silent, so the alarm is
   `RETURNED` and stays in the list until someone acknowledges it. The arrival of the first packet at 14:00:08 was
   inside the 10-second grace after AOS, so it raised nothing.
3. After LOS the satellite is below the station's minimum elevation, and silence is expected. An alarm there would
   fire after every single pass and teach operators to ignore it.
4. A mismatch almost always means the wrong TLE was pasted. Storing it under 25544 would silently add a satellite
   nobody asked for, and 99999 would keep its old orbit. A `400` makes the mistake visible at once.
5. The CelesTrak element set, because it has the newer epoch and the newest epoch wins. You didn't have to do
   anything: the refresh kept asking CelesTrak and logged "not on CelesTrak yet" at INFO until it was listed.
6. No station has the configured name anymore, so after the next plan update the ground can't predict passes. New
   packets are `UNKNOWN`, and the contact watch has no passes to watch, so no loss-of-signal alarm can be raised.
   The fix is to update `groundstation.tmtc.link.station`.
</details>
