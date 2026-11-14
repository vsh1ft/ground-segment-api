# Tutorial: real telemetry from SatNOGS

This tutorial follows [the XTCE mission database tutorial](xtce-mission-database.md) and
[the tracking and Doppler tutorial](tracking-and-doppler.md). Until now every telemetry value in the archive came
from the simulator. Here the service takes frames received from a real satellite, FrontierSat, from two sources:
frames that stations of the SatNOGS network received, and frames an amateur received with their own radio. It
decodes both into engineering values with the same XTCE machinery and keeps track of which is which. You will see
where SatNOGS keeps those frames, what a real frame looks like byte by byte, how one satellite's format becomes a
mission database, and what real data does that the simulator never did.

There is no code to write. The last part maps everything to the planned code.

---

## Part 0: what SatNOGS gives us

[SatNOGS](https://satnogs.org) is a network of several hundred amateur ground stations. A satellite operator or any
volunteer schedules an **observation**: one station records one pass of one satellite. The station's software
demodulates and deframes the signal, and uploads every frame it decoded.

The **SatNOGS Network API** lists observations publicly, without an account:

```
GET https://network.satnogs.org/api/observations/?norad_cat_id=69015
```

| Field | Example | Meaning |
|---|---|---|
| `id` | `15386644` | observation ID |
| `start`, `end` | `2026-11-08T18:58:28Z` | the pass the station recorded |
| `station_name`, `station_lat`, `station_lng`, `station_alt` | `Dtec Station`, `25.12`, `55.377` | where the frames were heard |
| `status` | `good` | a volunteer's verdict on the recording; often still `unknown` for hours |
| `demoddata` | list of URLs | one file per decoded frame |

A demoddata file holds one frame. Its name carries the time the station received it, to the second:
`data_15386644_2026-11-08T19-03-34_3` (the `_3` tells apart frames of the same second). Most stations upload raw
bytes; some older clients upload the frame as hexadecimal text, so a file made only of hex digits is read as hex.

Results come 25 observations at a time, newest first. The response's `Link` header holds the URL of the next page,
as a cursor, which keeps pages stable while new observations arrive.

All SatNOGS data is published under **CC BY-SA 4.0**: anyone may use it, as long as SatNOGS and the stations are
credited and derived data is shared under the same terms. The service keeps the station of every frame for that
reason, and the README says where the data comes from.

The SatNOGS **DB** also has a telemetry endpoint, but it needs a personal API token. Using the public Network API
means anyone who runs this project gets real data without signing up.

---

## Part 1: FrontierSat

FrontierSat (CTS-SAT-1) is a 3U cubesat built by CalgaryToSpace, a student team at the University of Calgary,
launched in May 2026, NORAD ID 69015. It transmits on 70 cm with a GomSpace AX100 radio, and SatNOGS stations hear
it many times a day. The team publishes its packet formats in its firmware repository.

Its frames are **not** AX.25, the format most amateur satellites use. The AX100 radio carries **CSP** packets
(CubeSat Space Protocol), and the radio itself takes care of synchronisation and error correction. What a SatNOGS
station uploads is the CSP packet:

```
┌──────────────┬──────────────────────────────────────────────┬──────────────┐
│  CSP header  │   payload: packet type, then its contents    │   CRC-32C    │
│   4 bytes    │                                              │   4 bytes    │
└──────────────┴──────────────────────────────────────────────┴──────────────┘
```

| Byte 4, packet type | Content | In this round |
|---|---|---|
| `0x21` | extended beacon v5+: 198 bytes of housekeeping | decoded into about 90 parameters |
| `0x30` | a raw GNSS position log | stored, not decoded |
| `0x01`, `0x03`, `0x04`, `0x10`, `0x20` | basic beacon, log text, telecommand response, file chunk, older beacon | stored, not decoded |

The **CRC-32C** (the Castagnoli polynomial, as CSP uses) covers the header and the payload, and is stored
big-endian.

### Not every station uploads the same bytes

The first real import showed that SatNOGS stations upload FrontierSat frames in two forms. Some (Dtec Station,
N6FR, Swallowview on 8 November 2026) upload the whole frame, CRC included: a beacon is 206 bytes. Others (K9YO-UHF,
MAUSyagi-AK, mpippins, VU2JEK_Nitin, WA6RSV) upload frames exactly 4 bytes shorter, 202 bytes: their decoder checks
the CRC and removes it. 67 of the 146 full beacons received in two days were also received, byte for byte, without
their CRC by a station of the other kind.

So the service **normalizes** every frame before anything else:

| Frame as received | Stored as | `crcChecked` |
|---|---|---|
| ends in a CRC-32C that matches the rest | the rest, without the CRC | `true` |
| doesn't end in a matching CRC | as it came, assumed already checked and stripped by the station's decoder | `false` |

Both kinds are decoded. A frame damaged after the radio's error correction would also land in the second row, but
the AX100's Reed-Solomon coding makes that rare, and the station's decoder usually drops such frames anyway. Because
frames are compared after normalizing, the 206-byte and the 202-byte reception of one beacon are one frame with two
receptions.

The check is set per satellite, next to its mission database:

```yaml
groundstation:
  tmtc:
    mission-databases:
      - norad-id: 69015
        location: classpath:mdb/frontiersat.xml
        frame-check: CRC32C_TRAILER
```

**All multi-byte fields in the payload are little-endian**: the least significant byte comes first. The simulator
and the CCSDS headers are big-endian. The difference matters at once: the two bytes `1E 39` of the battery voltage
read little-endian are 0x391E = 14 622 mV, a plausible 14.6 V; read big-endian they would be 0x1E39 = 7 737 mV,
plausible too, and wrong.

---

## Part 2: one real frame, byte by byte

Frame 3 of observation 15386644, received in the United Arab Emirates at 19:03:34 UTC on 8 November 2026, starts:

```
C2 A2 8A 00   21   43 54 53 31   01   00   C2 33 07 19   3C 66 09 04   B2 98 6D BB A1 01 00 00   ...
└──┬──────┘   │    └────┬────┘   │    │    └────┬────┘   └────┬────┘   └──────────┬──────────┘
   │          │         │        │    │         │             │                   └ unix_epoch_time_ms
   │          │         │        │    │         │             └ duration_since_last_uplink_ms
   │          │         │        │    │         └ uptime_ms
   │          │         │        │    └ active_rf_switch_control_mode
   │          │         │        └ active_rf_switch_antenna
   │          │         └ satellite_name, "CTS1"
   │          └ packet type 0x21, extended beacon v5+
   └ CSP header
```

Read little-endian:

| Field | Bytes | Value | Engineering value |
|---|---|---|---|
| `uptime_ms` | `C2 33 07 19` | 0x190733C2 = 419 902 402 | 4 days 20 h 38 min since the computer booted |
| `unix_epoch_time_ms` | `B2 98 6D BB A1 01 00 00` | 1 794 145 884 338 | **2026-11-08 13:51:24.338 UTC** |
| `obc_temperature_cC` | `6D 0B 00 00` | 2925 | 29.25 °C |
| `obc_adc_battery_voltage_mV` | `1E 39` | 14 622 | 14.622 V |
| `eps_battery_voltage_mV` | `00 00` | 0 | (not reported; see below) |
| `end_message` | `20 53 35 00` | `" S5"` | a stored history beacon |

Two lessons from one frame:

- **The frame is five hours old.** The beacon was made on board at 13:51:24 and heard at 19:03:34. The end message
  `" S5"` says so too: FrontierSat stores beacons in a ring buffer and sends old ones as history between live ones,
  which end in `" X5"`. A value must be filed under the time the spacecraft **measured** it, not the time the ground
  heard it, or a parameter's history jumps back and forth by hours.
- **A value of 0 can mean "not reported".** The team's comments say the power system's battery voltage reading
  often fails, and the computer's own ADC reading is the one to trust. The archive stores what the spacecraft sent;
  knowing which value to believe is mission knowledge, and the mission database's descriptions carry it.

---

## Part 3: from a C struct to XTCE

The team defines the beacon as a tightly packed C struct, `COMMS_beacon_extended_packet_v5plus_t`, in its firmware.
Every field becomes an XTCE parameter, in order, in a container:

```
FRONTIERSAT_FRAME                 the whole frame
  CSP_PRIORITY ... CSP_FLAGS      the 4-byte CSP header, as bit fields
  PACKET_TYPE                     enumeration: BEACON_BASIC, LOG_MESSAGE, ..., BEACON_EXTENDED_V5PLUS, GNSS_SAMPLE
    BEACON_EXTENDED_V5            restricted to PACKET_TYPE == BEACON_EXTENDED_V5PLUS
      SATELLITE_NAME, ACTIVE_RF_SWITCH_ANTENNA, UPTIME, ... about 90 parameters ... ADCS_ESTIMATED_YAW_ANGLE
```

| C type | XTCE |
|---|---|
| `uint8_t`, `uint16_t`, `uint32_t` | `IntegerParameterType`, unsigned, 8 / 16 / 32 bits, **`byteOrder="leastSignificantByteFirst"`** |
| `int16_t`, `int32_t` | the same, `twosComplement` |
| `uint64_t` | 64 bits |
| enums (`uint8_t` with named values) | `EnumeratedParameterType` with the team's labels |
| `char[4]` | `StringParameterType` with a fixed size of 32 bits |
| a value in mV, cC (hundredths of a degree), cW | `FloatParameterType` with a polynomial calibrator: 0.001 V, 0.01 °C, 0.01 W |
| `unix_epoch_time_ms` | `AbsoluteTimeParameterType`: epoch 1970-01-01, scale 0.001 s |
| `uint8_t[6]`, `uint8_t[7]` (packed bits, reserved) | skipped with a fixed-size spacer |

Our XTCE reader supports only some of this today. This round adds:

| Addition | Why |
|---|---|
| little-endian integers (`byteOrder="leastSignificantByteFirst"`) | every FrontierSat field |
| integers of up to 64 bits | `unix_epoch_time_ms` |
| `StringParameterType` of a fixed size | `SATELLITE_NAME`, `END_MESSAGE` |
| `AbsoluteTimeParameterType` with an epoch and a scale | the on-board time |
| a fixed-size gap in a container | reserved and packed bytes |

A container with an absolute time parameter gives its values their **generation time**: the time the spacecraft
measured them. A container without one, such as the simulator's, keeps using the generation time it has today.

No limits are defined for FrontierSat: the team hasn't published operating ranges, and inventing them would raise
alarms nobody can trust. They can be added later in the mission database, and the alarm module picks them up.

---

## Part 4: the same frame, heard by several stations

A good pass is often recorded by several stations, and each uploads the frames it heard. The same beacon then
arrives once per station. Every FrontierSat beacon carries the uptime in milliseconds, so two identical frames are
the same frame, not two measurements that happen to agree.

| Table | One row per | Holds |
|---|---|---|
| `received_frame` | distinct frame (satellite + bytes) | the bytes, the time it was first heard, the decoded container, whether the CRC matched |
| `frame_reception` | time a station heard a frame | **source** (`SATNOGS` or `OWN`), station, reception time; for `SATNOGS` the station's name, position and observation ID, for `OWN` the ID of one of your stations from `/stations` |

A frame is decoded and its values are stored **once**, when it is first received, whatever the source. Its later
receptions are added to its list. A beacon heard by your own antenna and by three SatNOGS stations is one frame with
four receptions: you see at once that your station heard it, and how it did compared with others on the same pass.
The list also credits the SatNOGS stations, as CC BY-SA asks.

### Frames from your own radio

An amateur with an SDR and a decoder such as SatDump or gr-satellites gets the same CSP frames a SatNOGS station
uploads. They send them to the service:

```
POST /satellites/69015/frames
Content-Type: application/json

{"station": "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42", "receivedAt": "2026-11-08T19:03:34.120Z",
 "frame": "C2A28A0021435453310100C23307193C..."}
```

| Situation | Response |
|---|---|
| a frame the service hasn't seen | `201 Created`, the frame with its receptions |
| a frame already received, from SatNOGS or another station | `200 OK`, the frame with the new reception added |
| the same frame from the same station at the same time again | `200 OK`, nothing added, so a script can safely send a frame twice |
| frame not hex, empty, or longer than 2048 bytes | `400` |
| unknown station, or satellite outside the catalog | `404` |

A frame from your own station counts the same as one from SatNOGS: decoded once, values in the same archive. Only
its reception says where it came from. A live feed straight from a decoder (KISS over TCP) comes with the station
control round.

The parameter values go into the **same** parameter archive as the simulator's, so
`GET /satellites/69015/parameters` and `/parameters/OBC_TEMPERATURE/values` work exactly as for the simulator.

---

## Part 5: the import

| Setting | Default | Meaning |
|---|---|---|
| `groundstation.satnogs.import.satellites` | `69015` | NORAD IDs whose frames are imported |
| `groundstation.satnogs.import.interval` | 1 hour | time between imports; the first runs at start |
| `groundstation.satnogs.import.look-back` | 2 days | how far back the very first import of a satellite goes |
| `groundstation.satnogs.import.settle` | 1 hour | only observations that ended at least this long ago are imported |

Each import, for each satellite, walks from where the last one ended to the settle time, **oldest first, one hour
at a time**. FrontierSat is popular: about 25 observations an hour, so two days is around a thousand observations
and forty pages. For each hour it asks for the observations that end in that hour and start no more than 30
minutes before it (`start` and `end` filters of the API), so an observation that straddles two hours is never
missed; overlaps cost a few extra downloads, and deduplication makes them harmless. After each finished hour it
saves its position, so an import that SatNOGS throttles or that loses its connection keeps the hours it finished
and continues from there next time.

It takes every observation whatever its `status` (volunteers vet recordings hours or days later, and the frames
are real either way) and downloads the frames one by one. A frame file that can't be downloaded (the storage
service occasionally answers 503) is logged and skipped rather than blocking the import.

The **settle** time is there because stations upload frames after the pass ends, usually within minutes but
sometimes later. An observation that ended five minutes ago may still be missing frames, and since the import never
goes back to an observation it has done, it waits an hour before taking it.

The satellite must be in the catalog: if CelesTrak doesn't list FrontierSat in one of the refreshed groups, add it
with `POST /satellites/69015`.
A satellite in the list without a mission database has its frames stored but not decoded; adding a database later
decodes the frames received from then on.

Downloads are made one after another, with a `User-Agent` that names the project, so the import never hammers the
SatNOGS servers. With about twenty FrontierSat observations a day and up to a hundred frames each, an hourly import
downloads a few hundred small files.

The Network API limits how often a client without an account may ask. A throttled request is answered with `429 Too
Many Requests` and a `Retry-After` header in seconds. The import then stops, keeps its position, and asks again only
after that time, or after one interval when the header is missing. The frames themselves are files on a storage
service, and downloading them is not throttled.

---

## Part 6: the API

```
GET /satellites/69015/frames?limit=20&before=4711
```

```json
{
  "frames": [
    {"id": 4712, "firstReceivedAt": "2026-11-08T19:03:34Z", "container": "BEACON_EXTENDED_V5",
     "generationTime": "2026-11-08T13:51:24.338Z", "crcChecked": true, "length": 202,
     "raw": "C2A28A0021435453310100C23307193C...",
     "receptions": [
       {"source": "SATNOGS", "station": "Dtec Station", "latitudeDeg": 25.12, "longitudeDeg": 55.377,
        "altitudeM": 32, "observation": 15386644, "receivedAt": "2026-11-08T19:03:34Z"},
       {"source": "OWN", "station": "Vilnius", "stationId": "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42",
        "latitudeDeg": 54.675, "longitudeDeg": 25.234, "altitudeM": 114, "observation": null,
        "receivedAt": "2026-11-08T19:03:34.120Z"}
     ]}
  ],
  "nextBefore": 4711
}
```

Newest frame first, paged with `before` like the telemetry packets. `source=OWN` or `source=SATNOGS` keeps only
frames with at least one reception of that source, and `station` only frames one of your stations heard. `container` is the deepest container the
mission database matched, or `null` for a frame that wasn't decoded; `generationTime` is `null` when the frame has no
on-board time.

| Method and path | Result |
|---|---|
| `GET /satellites/{noradId}/frames?source=&station=` | frames with their receptions, newest first |
| `POST /satellites/{noradId}/frames` | a frame from your own station |
| `GET /satellites/{noradId}/parameters` | FrontierSat's beacon parameters, as for the simulator |
| `GET /satellites/{noradId}/parameters/{name}/values` | their history, by generation time |

---

## Part 7: how this maps to the code

| Piece | Code |
|---|---|
| `xtce` | little-endian and 64-bit integers, fixed-size strings, absolute time with epoch and scale, fixed-size container gaps; a test decodes the real frame above and compares every field with values computed independently from the team's C struct |
| mission database | `orbit_service/src/main/resources/mdb/frontiersat.xml`, written from the firmware struct, with the team's enum labels and comments as descriptions |
| mission databases | `MissionDatabases` holds one database per satellite: the link's, plus those listed in `groundstation.tmtc.mission-databases`, each with a NORAD ID, a location and a frame check (`CRC32C_TRAILER` for FrontierSat, `NONE` by default) |
| `tmtc` | a public entry point that takes a frame of any satellite with its reception: deduplicates, checks the frame with the satellite's frame check, decodes, stores values with their generation time, and publishes the same `ParameterValuesReceived` event the alarm module listens to; tables `received_frame` and `frame_reception`; `parameter_value` refers to a packet **or** a frame |
| new module `satnogs` | the Network API client, the hourly import with look-back and settle time, the import state per satellite |
| API | `GET /satellites/{noradId}/frames` with `source` and `station` filters; `POST /satellites/{noradId}/frames` for your own frames |
| documents | requirements in `docs/requirements/satnogs.yml`, README section with the CC BY-SA attribution |

AX.25 satellites, the GNSS log, a live KISS feed from a decoder, the DB telemetry API with a token, uploading your
own frames to SatNOGS, and limits for FrontierSat are left for later rounds.

---

## Exercises

1. A FrontierSat field `eps_battery_temperature_0_cC` has the bytes `44 06`. What is its engineering value?
2. A SatNOGS station hears a beacon at 19:03:34, and your own station, whose frame you upload, hears the same
   beacon at 19:03:35. How many rows does that make in `received_frame`, in `frame_reception` and in the parameter
   archive, and what are the sources of the receptions?
3. Why does the import wait an hour after an observation ends, instead of importing it right away and coming back
   later?
4. A beacon ends in `" S5"` and its `unix_epoch_time_ms` is three hours before it was received. Under which time is
   `OBC_TEMPERATURE` stored, and why?
5. Why are the frames' bytes, not their decoded values, the thing to deduplicate on?

<details>
<summary>Answers</summary>

1. Little-endian: 0x0644 = 1604 hundredths of a degree, so 16.04 °C.
2. One distinct frame, two receptions (one `SATNOGS`, one `OWN`), and one set of parameter values: the values are
   stored once, when the frame is first received, whichever source that was.
3. The import never revisits an observation it has done, which keeps it simple and cheap. Coming back would mean
   remembering which observations might still change and fetching them again. Waiting until stations have almost
   certainly finished uploading gets the same result with less work.
4. Under the on-board time, three hours before reception: that is when the spacecraft measured it. Filed under the
   reception time, the history would show an old temperature as if it were current.
5. Two different frames can decode to the same values (for example, two beacons a second apart while nothing
   changed), and they are two measurements. The bytes of two receptions of the same beacon are identical down to the
   millisecond uptime, so identical bytes mean the same beacon.
</details>
