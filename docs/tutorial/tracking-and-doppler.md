# Tutorial: tracking and Doppler

This tutorial builds on [the passes chapter of the README](../../README.md#passes) and on
[the passes and loss of signal tutorial](passes-and-signal.md). A pass says **when** a satellite is above a
station. To actually hear it, a station also needs to know **where to point** the antenna at every moment of the
pass, and **which frequency to tune to**, because the satellite's motion shifts its signal. Here you will see what
a track is, how the Doppler shift arises and how big it gets, and how the service turns a satellite's transmitters
from the SatNOGS DB into a frequency to tune to, second by second.

There is no code to write. The last part maps everything to the planned code.

---

## Part 0: what a station needs during a pass

| The station needs | Because | The service gives |
|---|---|---|
| when the pass starts and ends | to be ready at the right time | `/passes` (exists) |
| where to point, every second | a directional antenna must follow the satellite across the sky | the **track**: azimuth and elevation per step |
| which frequency to tune to | the satellite moves at about 7 km/s, which shifts its frequency by several kilohertz | the **Doppler-corrected** frequency of each transmitter per step |
| on which frequency to transmit | the satellite's receiver also sees a shifted signal | the corrected **uplink** frequency per step |

A handheld Yagi antenna and a hand on the radio's tuning knob can follow a slow pass. A rotator and a computer-
controlled radio need the numbers. Programs such as Gpredict compute them on the station's own computer; this
service computes them on the server and serves them over the API, for any station it knows.

---

## Part 1: the track

A track is a list of points at a fixed step, one per second by default:

| Field | Meaning |
|---|---|
| `time` | UTC |
| `azimuthDeg` | compass direction, 0° north, 90° east, from 0 to below 360 |
| `elevationDeg` | angle above the horizon; negative means below it |
| `rangeKm` | distance from the station to the satellite |
| `rangeRateKmPerS` | how fast that distance changes: negative while the satellite approaches, positive while it moves away |

```
GET /satellites/25544/track?station=3f2a9c1e-...&from=2026-11-01T00:41:16Z&duration=PT9M&step=PT1S
```

The track covers any window, not only a pass: a client takes `start` and `end` from `/passes`, or asks for a
window around a pass to see the satellite rise. Points below the horizon are included, so the antenna can be
pointed at the rise azimuth before the satellite appears.

| Parameter | Default | Limits |
|---|---|---|
| `station` | required | a station ID |
| `from` | now | |
| `duration` | 15 minutes | more than 0, at most 1 day |
| `step` | 1 second | 100 ms to 1 hour |
| points | | duration / step + 1, at most 10 000 |

A pass of 9 minutes at 1 second is 541 points. A whole day at 10 seconds is 8641. A whole day at 1 second would be
86 401 points; that is a `400`, because a client that needs that should ask in pieces.

Elevation is **geometric**: the angle to the satellite's true position, without the bending of radio waves in the
atmosphere. Near the horizon the satellite actually appears about 0.5° higher. The pass predictions use the same
geometric elevation, so a track's elevation crosses the station's minimum elevation exactly at the pass's `start`
and `end`.

---

## Part 2: the Doppler shift

### Why the frequency moves

A transmitter that comes towards you squeezes its waves together: you receive a higher frequency than it sends.
One that moves away stretches them: you receive a lower one. Only the motion **along the line of sight** counts,
and that is exactly the range rate.

```
f_received = f_sent × (1 − range rate / c)        c = 299 792.458 km/s
```

The satellite moves at about 7.6 km/s, but along the line of sight a station sees at most about 7 km/s, at the
horizon of a high pass.

| Transmitter | Nominal | Range rate −7 km/s (approaching) | 0 (closest point) | +7 km/s (leaving) |
|---|---|---|---|---|
| ISS APRS, VHF | 145.825 MHz | +3.40 kHz | 0 | −3.40 kHz |
| cubesat beacon, UHF | 437.000 MHz | +10.20 kHz | 0 | −10.20 kHz |
| S-band | 2.4 GHz | +56 kHz | 0 | −56 kHz |

The shift grows with frequency. On VHF a narrow FM receiver with a few kilohertz of bandwidth copes without
retuning; on UHF a 1200-baud signal drifts out of a narrow filter, and on S-band nothing works without
correction.

### The S-curve

Over a pass the shift follows an S:

```
 shift
 +3.4 kHz ┤▔▔▔▔▔▔▔▔╲
          │          ╲
        0 ┼───────────╲──────────────── time
          │            ╲
 −3.4 kHz ┤             ╲▁▁▁▁▁▁▁▁▁▁
          AOS        closest       LOS
                     approach
```

The shift is largest at the start and the end, and passes through zero at the **closest approach**, which for a
pass is the moment of highest elevation. It changes fastest at the closest approach: for a high pass it sweeps
most of its range within a minute or two around the peak. That is why automatic tuning matters most in the middle
of a pass, even though the shift is largest at the edges.

### Uplink: the other direction

On the uplink, the satellite's receiver sees the shift. To be heard on the satellite's nominal frequency, the
station transmits on a frequency shifted the **opposite** way:

```
f_transmit = f_nominal / (1 − range rate / c)
```

While the satellite approaches, the station transmits a little **lower** than nominal, and the satellite receives
it exactly on nominal.

The formula is the first-order Doppler effect. The relativistic correction at 7 km/s is about 3 × 10⁻¹⁰ of the
frequency, under a millihertz on VHF, so it is left out.

---

## Part 3: which frequencies

The `radio` module already keeps every satellite's transmitters from the SatNOGS DB. A track lists the corrected
frequencies of the satellite's **active** transmitters:

| Transmitter in SatNOGS | Corrected frequencies in the track |
|---|---|
| transmitter (downlink only) | its downlink |
| transceiver | its downlink and its uplink |
| transponder | the low and high edges of its downlink band and of its uplink band |

The frequencies are listed once at the top of the response, and every point has a value for each of them, in the
same order:

```json
{
  "satellite": 25544, "station": "3f2a9c1e-...", "elementsEpoch": "2026-10-31T04:14:04Z", "step": "PT1S",
  "frequencies": [
    {"transmitter": "ZJxCeQmih9zDfYNVrB4wRN", "description": "Mode V APRS", "direction": "DOWNLINK",
     "nominalHz": 145825000},
    {"transmitter": "ZJxCeQmih9zDfYNVrB4wRN", "description": "Mode V APRS", "direction": "UPLINK",
     "nominalHz": 145825000}
  ],
  "points": [
    {"time": "2026-11-01T00:41:17Z", "azimuthDeg": 262.057, "elevationDeg": 5.024, "rangeKm": 1876.78,
     "rangeRateKmPerS": -6.762, "frequenciesHz": [145828289, 145821711]},
    ...
  ]
}
```

A transmitter that isn't in the SatNOGS DB, or the simulator's own downlink, can be given directly:
`downlinkHz=437000000` and `uplinkHz=145900000` replace the SatNOGS list with these frequencies. Frequencies are
rounded to whole hertz.

A satellite without active transmitters, and no frequency parameters, gets a track with an empty `frequencies`
list. Pointing still works.

---

## Part 4: checking the numbers

The pass predictions are already checked against Skyfield, an independent SGP4 implementation in Python, to
within 1 second. The track is checked the same way: for the fixed ISS element set over Vilnius, a script computes
azimuth, elevation, range and range rate at given times with Skyfield, and the test requires the service to agree.

| Quantity | Tolerance |
|---|---|
| azimuth, elevation | 0.01° |
| range | 0.1 km |
| range rate | 0.001 km/s, which is 0.5 Hz of Doppler on VHF |

The reference values come from `tools/reference/skyfield_track.py`, next to the existing `skyfield_passes.py`.

---

## Part 5: one pass, step by step

The ISS over Vilnius on 1 November 2026, the pass that peaks at 50° at 00:45:30 UTC, with the APRS downlink and
uplink on 145.825 MHz. These are the Skyfield reference values the tests use:

| Time | Azimuth | Elevation | Range | Range rate | Receive on | Transmit on |
|---|---|---|---|---|---|---|
| 00:41:17 (AOS) | 262.06° W | 5.02° | 1876.8 km | −6.762 km/s | 145.828 289 MHz | 145.821 711 MHz |
| 00:43:30 | 251.33° WSW | 20.90° | 1011.8 km | −5.994 km/s | 145.827 916 MHz | 145.822 084 MHz |
| 00:45:30 (peak) | 182.76° S | 50.07° | 545.7 km | +0.044 km/s | 145.824 979 MHz | 145.825 021 MHz |
| 00:47:30 | 115.09° ESE | 20.79° | 1017.3 km | +6.006 km/s | 145.822 079 MHz | 145.827 922 MHz |
| 00:49:43 (LOS) | 104.47° ESE | 5.02° | 1882.8 km | +6.761 km/s | 145.821 711 MHz | 145.828 289 MHz |

In the four minutes between 00:43:30 and 00:47:30 the downlink falls by 5.8 kHz, while in the two minutes from
AOS to 00:43:30 it moves by only 0.4 kHz. The middle of the pass is where a station without automatic tuning
loses the signal.

---

## Part 6: how this maps to the code

| Piece | Code |
|---|---|
| module | a new Spring Modulith module `tracking`; it uses `pass` (stations, Orekit setup), `elements` (the TLE) and `radio` (transmitters) |
| geometry | Orekit `TLEPropagator` and `TopocentricFrame`: azimuth, elevation, range and range rate from the same station frame the pass predictor uses |
| Doppler | a small `Doppler` value type: downlink `f × (1 − ṙ/c)`, uplink `f / (1 − ṙ/c)`, rounded to whole hertz |
| frequencies | the satellite's active transmitters from `radio`, expanded per Part 3, or the `downlinkHz` / `uplinkHz` parameters |
| API | `GET /satellites/{noradId}/track` with `station`, `from`, `duration`, `step`, `downlinkHz`, `uplinkHz`; 400 for a window of more than 10 000 points or invalid values, 404 for an unknown station or a satellite outside the catalog |
| reference | `tools/reference/skyfield_track.py` and a test comparing the track with its values |
| documents | requirements in `docs/requirements/tracking.yml`, README section |

The satellite's current position for maps, a CSV form of the track, refraction and rotator control (Hamlib
`rotctld`) are left for later rounds.

---

## Exercises

1. A 9-minute pass is tracked at a step of 500 ms. How many points does the track have, and is it allowed?
2. A cubesat transmits on 436.500 MHz. The range rate is +4.2 km/s. Which frequency should the station tune to?
3. Why does the Doppler shift pass through zero at the highest point of a pass, and not halfway between AOS and
   LOS in time?
4. A satellite's transponder takes uplinks between 145.900 and 146.000 MHz. Why must the station transmit a little
   lower than it wants the satellite to hear while the satellite approaches?
5. Why does the track report negative elevations instead of starting at the pass's AOS?

<details>
<summary>Answers</summary>

1. 9 × 60 / 0.5 + 1 = 1081 points, well under 10 000, so it's allowed.
2. 436 500 000 × (1 − 4.2 / 299 792.458) = 436 493 885 Hz, about 6.1 kHz below nominal, because the satellite is
   moving away.
3. Zero shift means the range isn't changing, which is the moment of closest approach, and the highest elevation is
   also the closest point. The pass is roughly symmetric in time around it, so for most passes it is close to
   halfway too; what defines it is the geometry, not the clock.
4. While approaching, the satellite's receiver sees every signal shifted upwards. Transmitting a little lower makes
   the signal arrive exactly where the operator wants it in the transponder's band.
5. A rotator needs time to turn. With points before AOS, it can be pointed at the rise azimuth in advance, and the
   track continues smoothly through AOS instead of starting with a jump.
</details>
