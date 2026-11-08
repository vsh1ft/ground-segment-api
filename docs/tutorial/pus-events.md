# Tutorial: events (PUS service 5)

This tutorial follows [the mission database tutorial](xtce-mission-database.md). Housekeeping tells the ground
what the spacecraft's values *are*; events tell it what *happened*. Here you will see what an event packet looks
like, how the simulator will produce events, how the XTCE database describes them, and what the ground does with
them.

No code to write. The last part maps everything to the planned code.

---

## Part 0: why events exist

A housekeeping report every second is enough to plot a battery voltage. It is not enough to answer the question
an operator asks at the start of every pass: *what did the spacecraft do while we couldn't see it?*

Most of a low Earth orbit is spent out of contact with any one ground station. During that time the on-board
software watches its own values, detects problems and reacts, for example by switching off a payload or dropping
into SAFE mode. That software is called **FDIR** (Fault Detection, Isolation and Recovery). Every step it takes is
reported as an **event**, and the event log is how the ground reconstructs the story afterwards.

| | Housekeeping, service 3 | Events, service 5 |
|---|---|---|
| sent | periodically | when something happens |
| answers | "what is the value now?" | "what changed, and how bad is it?" |
| a lost packet means | a gap in a plot | possibly the only clue to an anomaly |
| everyday analogy | metrics | log lines with a level |

---

## Part 1: the event packets

PUS-C service 5 defines four telemetry reports. **The subtype is the severity**:

| Message | Name in PUS-C | Severity | Meaning |
|---|---|---|---|
| TM[5,1] | informative event report | INFO | normal but worth knowing: a mode change, a deployment finished |
| TM[5,2] | low severity anomaly report | LOW | something is off, no action needed yet |
| TM[5,3] | medium severity anomaly report | MEDIUM | something is wrong; look at it during the pass |
| TM[5,4] | high severity anomaly report | HIGH | the spacecraft protected itself or needs the ground now |

(Service 5 also has telecommands to enable and disable individual events, TC[5,5] and TC[5,6], and to ask which
are disabled, TC[5,7] answered by TM[5,8]. They belong to the telecommand phase.)

The user data of every event report has the same shape:

```
┌──────────────────────┬──────────────────────────────────────────────┐
│ event definition ID  │ auxiliary data                                │
│ uint16 on our link   │ depends on the event; may be empty            │
└──────────────────────┴──────────────────────────────────────────────┘
```

The **event definition ID** says which event it is. PUS leaves its size to the mission; 16 bits is common and is
what we use. The **auxiliary data** carries whatever helps to understand the event, such as the voltage that
crossed a threshold.

---

## Part 2: the simulator's events

Today the simulator is always NOMINAL and its values follow a sine that never leaves a safe band. For events it
gets thresholds and a small piece of FDIR. Battery voltage and temperature both follow the sunlight: they are low in
eclipse and high in sunlight.

| ID | Name | Report | When | Auxiliary data |
|---|---|---|---|---|
| 1 | `BATTERY_LOW` | TM[5,2] | battery voltage falls below 7.15 V | battery voltage, uint16 mV |
| 2 | `BATTERY_RECOVERED` | TM[5,1] | battery voltage is back at 7.25 V or more | battery voltage, uint16 mV |
| 3 | `TEMPERATURE_HIGH` | TM[5,3] | temperature rises above 33.0 °C | temperature, int16 in 0.1 °C |
| 4 | `TEMPERATURE_NORMAL` | TM[5,1] | temperature is back at 30.0 °C or less | temperature, int16 in 0.1 °C |
| 5 | `SAFE_MODE_ENTERED` | TM[5,4] | FDIR's reaction to `BATTERY_LOW`: the mode becomes SAFE | none |
| 6 | `NOMINAL_MODE_RESUMED` | TM[5,1] | FDIR's reaction to `BATTERY_RECOVERED`: the mode becomes NOMINAL | none |

Two details worth noticing:

- **Hysteresis.** "Low" starts below 7.15 V but ends only at 7.25 V. With a single threshold, a value hovering
  around it would raise LOW, RECOVERED, LOW, RECOVERED every second. Real FDIR always uses two thresholds or a
  persistence count for this reason.
- **Cause and reaction are separate events.** `BATTERY_LOW` is the observation; `SAFE_MODE_ENTERED` is what the
  spacecraft did about it. The housekeeping `SPACECRAFT_MODE` changes too, so the operator sees the same story in
  the plot and in the log.

With the real 92-minute orbit you would wait an hour for an event, so the orbit period becomes configurable
(`SIMULATOR_ORBIT_PERIOD`, for example `PT10M` for a demo). Each orbit then produces all six events.

---

## Part 3: an event packet by hand

`BATTERY_LOW` at 2026-11-08T09:00:00Z, battery at 7149 mV, sequence count 812, first low-severity report (counter 0):

```
08 64 C3 2C 00 12 | 20 05 02 00 00 00 00 81 82 98 B5 00 00 | 00 01 | 1B ED | 00 9A
```

| Bytes | Field | Value |
|---|---|---|
| `08 64` | version, type, secondary header flag, APID | TM, secondary header, APID 100 |
| `C3 2C` | sequence flags, count | unsegmented, 0x32C = 812 |
| `00 12` | packet data length | 18, so 19 bytes follow |
| `20` | PUS version, time status | 2, 0 |
| `05 02` | service, subtype | **TM[5,2]: low severity anomaly** |
| `00 00` | message type counter | 0, the first TM[5,2] |
| `00 00` | destination | ground |
| `81 82 98 B5 00 00` | CUC time | 2172819637 s after 1958 TAI = 09:00:00 UTC |
| `00 01` | event definition ID | 1 = `BATTERY_LOW` |
| `1B ED` | auxiliary data | 7149 mV = 7.149 V |
| `00 9A` | CRC | |

The `SAFE_MODE_ENTERED` report that follows has no auxiliary data, so it is two bytes shorter:

```
08 64 C3 2D 00 10 | 20 05 04 00 00 00 00 81 82 98 B5 00 00 | 00 05 | B2 26
```

Note the separate counters: the sequence count goes 812 → 813 (per APID, every packet), while the message type
counter of TM[5,4] starts at 0 independently of TM[5,2] (per service and subtype).

---

## Part 4: describing events in XTCE

Nothing new is needed in the XTCE subset; events use the same building blocks as housekeeping.

**The event ID is an enumeration.** Its label is the event name, and the `shortDescription` of each `Enumeration`
is the text an operator reads:

```xml
<EnumeratedParameterType name="event_id_t">
  <IntegerDataEncoding sizeInBits="16"/>
  <EnumerationList>
    <Enumeration value="1" label="BATTERY_LOW" shortDescription="Battery voltage below 7.15 V"/>
    <Enumeration value="2" label="BATTERY_RECOVERED" shortDescription="Battery voltage back at 7.25 V or more"/>
    <Enumeration value="5" label="SAFE_MODE_ENTERED" shortDescription="FDIR switched the spacecraft to SAFE mode"/>
    …
  </EnumerationList>
</EnumeratedParameterType>

<Parameter name="EVENT_ID" parameterTypeRef="event_id_t"/>
<Parameter name="EVENT_BATTERY_VOLTAGE" parameterTypeRef="voltage_t"/>
```

The auxiliary values get their own parameters (`EVENT_BATTERY_VOLTAGE`, `EVENT_TEMPERATURE`) but reuse the
housekeeping types, so the calibration lives in one place. Separate parameters keep the housekeeping history of
`BATTERY_VOLTAGE` a clean once-per-second series.

**The containers add one abstract level and one child per event:**

```
CCSDS_PACKET
  └─ PUS_TM
       ├─ PUS_HK_REPORT ── HK_STRUCTURE_1
       └─ PUS_EVENT                      service 5; entry: EVENT_ID
            ├─ EVENT_BATTERY_LOW         APID 100, EVENT_ID == BATTERY_LOW;       entry: EVENT_BATTERY_VOLTAGE
            ├─ EVENT_BATTERY_RECOVERED   APID 100, EVENT_ID == BATTERY_RECOVERED; entry: EVENT_BATTERY_VOLTAGE
            ├─ EVENT_SAFE_MODE_ENTERED   APID 100, EVENT_ID == SAFE_MODE_ENTERED; no entries
            └─ …
```

```xml
<SequenceContainer name="EVENT_BATTERY_LOW">
  <EntryList>
    <ParameterRefEntry parameterRef="EVENT_BATTERY_VOLTAGE"/>
  </EntryList>
  <BaseContainer containerRef="PUS_EVENT">
    <RestrictionCriteria>
      <ComparisonList>
        <Comparison parameterRef="APID" value="100"/>
        <Comparison parameterRef="EVENT_ID" value="BATTERY_LOW"/>
      </ComparisonList>
    </RestrictionCriteria>
  </BaseContainer>
</SequenceContainer>
```

Two things from the mission database tutorial at work here:

- `value="BATTERY_LOW"` compares the **engineering** value, the label, because `useCalibratedValue` defaults to
  true. The database reads like a specification instead of a list of magic numbers.
- Events without auxiliary data still get a container, with an empty `EntryList`. Otherwise decoding would end in
  the abstract `PUS_EVENT`, which means "a packet this database doesn't fully know".

The restriction does not check the subtype. Which severity an event has is the spacecraft's decision, and the
ground should show what the spacecraft said, not what the database expected.

---

## Part 5: what the ground does with an event

For every TM[5,1] to TM[5,4] packet, after archiving it as usual:

1. **Severity from the header.** The subtype gives INFO, LOW, MEDIUM or HIGH. This needs only the codec, not the
   mission database.
2. **Identity from the mission database.** Decoding gives `EVENT_ID`: its raw value, its label (the name) and the
   label's description (the text).
3. **Auxiliary values.** They are stored as parameter values like every other decoded value; the event points to
   its packet, and the API returns the values of the event's own container with it.
4. **Never lose an event.** If the packet can't be decoded (unknown event ID, too short, no mission database for
   the satellite), the event is still logged, with its severity and time but without a name. An anomaly report
   the ground can't name is exactly the one an operator needs to see.

The result, as the API will return it:

```json
{"id": 7, "severity": "LOW", "eventId": 1, "name": "BATTERY_LOW",
 "description": "Battery voltage below 7.15 V", "generationTime": "2026-11-08T09:00:00Z", "packetId": 812,
 "values": [{"parameter": "EVENT_BATTERY_VOLTAGE", "raw": 7149, "engineering": 7.149}]}
```

---

## Part 6: how this maps to the code

| Piece | Code |
|---|---|
| message types | `MessageType.INFORMATIVE_EVENT_REPORT` … `HIGH_SEVERITY_ANOMALY_REPORT` in `ccsds` |
| simulator state | `Spacecraft.step(now)` keeps the mode and the alarm states and returns the housekeeping and the events of the step; `EventDefinition` lists the six events |
| simulator packets | `TelemetryGenerator.step(now)` sends the step's event reports, then its housekeeping, with one message type counter per service and subtype |
| simulator config | `SIMULATOR_ORBIT_PERIOD`, ISO-8601, default `PT92M` |
| mission database | `simulator.xml`: `event_id_t`, `EVENT_ID`, `EVENT_BATTERY_VOLTAGE`, `EVENT_TEMPERATURE`, `PUS_EVENT` and one `EVENT_…` container per event |
| `xtce` | `EnumeratedParameterType.description(raw)`; `MissionDatabase.container(name)` |
| ground | `Severity.of(messageType)`; `TelemetryReception` logs a `StoredEvent` for every TM[5,1..4], decoded or not |
| archive | table `event` (V8): satellite, packet, severity, event ID, name, description, container, generation time |
| API | `GET /satellites/{noradId}/events` with `minSeverity`, `from`, `to`, `limit`, `before`; the auxiliary values are the packet's values for the parameters of the event's container |
| documents | ICD issue 3 (section 4.2), requirements EVT-010 to EVT-030 |

---

## Exercises

1. Decode `08 64 C3 30 00 12 20 05 03 00 00 00 00 81 82 98 B5 00 00 00 03 01 4F …`. Which event, which
   severity, what value?
2. The battery voltage goes 7160, 7149, 7151, 7148, 7240, 7260 mV on consecutive seconds. Which events are sent?
3. Why does `PUS_EVENT` restrict on the service type only, and not on the subtype?
4. A TM[5,4] arrives with event ID 99, which the database doesn't know. What should the event log show?
5. Why does the simulator report `SAFE_MODE_ENTERED` as a separate event instead of putting "and switched to SAFE"
   into the `BATTERY_LOW` report?

<details>
<summary>Answers</summary>

1. Service 5, subtype 3: MEDIUM. Event ID 3 = `TEMPERATURE_HIGH`. Auxiliary `01 4F` = 335 → 33.5 °C.
2. At 7149: `BATTERY_LOW` (TM[5,2]) and `SAFE_MODE_ENTERED` (TM[5,4]). Nothing at 7151, 7148 or 7240, because
   the spacecraft is already in the low state and 7240 is still below 7.25 V. At 7260: `BATTERY_RECOVERED`
   (TM[5,1]) and `NOMINAL_MODE_RESUMED` (TM[5,1]).
3. The subtype is the severity the spacecraft assigned. Restricting on it would make the database reject a
   report whose severity changed in a new on-board software version, and the ground would lose the event's name
   exactly when something unusual happened.
4. A HIGH event with event ID 99, its time and its packet, and no name or description. It must not be dropped:
   it is a high-severity anomaly, and the missing name means the database is out of date, which is a second
   problem worth noticing.
5. They are different facts with different severities: the observation (LOW) and the spacecraft's autonomous
   reaction (HIGH). Operators filter by severity, and FDIR actions are what they must review first; ground
   procedures and statistics also count them separately.
</details>
