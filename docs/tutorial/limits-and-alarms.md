# Tutorial: limits and alarms

This tutorial follows [the events tutorial](pus-events.md). Events are the spacecraft telling the ground that
something happened. Limits are the ground checking the values for itself, and alarms are how it makes sure an
operator notices when a value goes outside its limits. Here you will see how XTCE describes limits, how each value is
checked, and how an alarm lives from the moment it is raised until it is closed.

There is no code to write. The last part maps everything to the planned code.

---

## Part 0: why the ground checks values itself

The spacecraft already watches its battery and raises `BATTERY_LOW` below 7.15 V. Why should the ground check too?

| | On-board FDIR | Ground limits |
|---|---|---|
| purpose | protect the spacecraft *now*, without the ground | warn an operator *before* FDIR has to act |
| thresholds | as late as safely possible, because acting costs the mission (SAFE mode stops the payload) | earlier, so a person can still decide |
| changed by | uploading new on-board software or parameters | editing the mission database on the ground |
| sees | everything, all the time | only what is downlinked, only during passes |

A ground warning at 7.20 V means an operator sees the battery trend while the spacecraft is still nominal.
If the voltage reaches 7.15 V, FDIR acts, and the ground's critical limit agrees that this is serious.

The two kinds of check complement each other: FDIR is the safety net, and ground limits are an early warning.

---

## Part 1: limit levels

Every checked value ends up in one of three **monitoring levels**:

| Level | Meaning | Usual display |
|---|---|---|
| `IN_LIMITS` | normal | green |
| `WARNING` | unusual; watch it | yellow |
| `CRITICAL` | out of the safe range; act | red |

XTCE defines five levels: watch, warning, distress, critical and severe. Warning and critical are the pair almost
every mission uses, and they are what this round supports.

A range defines what is **allowed**. A value outside the warning range is a warning. A value outside the critical
range is critical. The critical range is therefore wider than the warning range:

```
 critical │ warning │          in limits          │ warning │ critical
──────────┼─────────┼─────────────────────────────┼─────────┼──────────  battery voltage, V
        7.12      7.20                          7.80      7.90
```

---

## Part 2: limits in XTCE

Limits are part of the **parameter type**, in a `DefaultAlarm` after the encoding:

```xml
<FloatParameterType name="battery_voltage_t">
  <UnitSet><Unit>V</Unit></UnitSet>
  <IntegerDataEncoding sizeInBits="16" encoding="unsigned">
    <DefaultCalibrator>
      <PolynomialCalibrator>
        <Term exponent="1" coefficient="0.001"/>
      </PolynomialCalibrator>
    </DefaultCalibrator>
  </IntegerDataEncoding>
  <DefaultAlarm>
    <StaticAlarmRanges>
      <WarningRange minInclusive="7.20" maxInclusive="7.80"/>
      <CriticalRange minInclusive="7.12" maxInclusive="7.90"/>
    </StaticAlarmRanges>
  </DefaultAlarm>
</FloatParameterType>
```

Points to notice:

- **Ranges are in engineering units**: volts, not millivolt counts. The calibrator runs first, then the check.
- **`rangeForm` defaults to `outside`**: the range is the allowed region, and values outside it are in alarm.
  XTCE also has `inside` (the range is the forbidden region). This round supports only `outside`.
- **Either side may be left open.** `<WarningRange maxInclusive="32.0"/>` checks only the upper side. Each side can
  be inclusive or exclusive (`minExclusive`, `maxExclusive`), but not both.
- **The ranges must nest.** Where both levels limit the same side, the warning limit has to lie inside the critical
  limit. Otherwise a value could be critical without first being a warning, so the reader rejects such a database.
  A side limited only by the critical range is fine: values beyond it go straight to critical.
- **Both numeric kinds can have limits.** `IntegerParameterType` has the same `DefaultAlarm`. Enumerated types
  use a different element, `EnumerationAlarmList`, which is out of scope for this round.

### What the reader rejects

XTCE alarms have more options than this round supports: `ContextAlarmList` (limits that depend on the mode),
`ChangeAlarmRanges` (rate of change), `AlarmMultiRanges`, watch, distress and severe ranges, `minViolations`
other than 1, `AlarmConditions` and `CustomAlarm`.

Until now, the reader **ignored** unsupported XTCE elements that are only descriptive, such as `LongDescription`
and `AliasSet`. Unsupported alarm elements are different: ignoring one would silently drop a limit the database
author expects to be checked. So the reader **rejects** a database that uses any of them, with an
`InvalidMissionDatabaseException` that names the element. A database that loads is then checked exactly as written.

### Limits belong to the type, not to the parameter

In XTCE, limits are part of the type, so every parameter of that type is checked the same way. In `simulator.xml`,
`BATTERY_VOLTAGE` (housekeeping) and `EVENT_BATTERY_VOLTAGE` (the value carried in a battery event) share
`voltage_t`. Adding limits to `voltage_t` would check the event values too, and every `BATTERY_LOW` event would
raise a second alarm for the same voltage.

The XTCE answer is a separate type. The housekeeping parameters get their own types with limits
(`battery_voltage_t`, `platform_temperature_t`), and the event values keep `voltage_t` and `temperature_t`
without limits. The calibration is then written twice. That costs a few lines, and in exchange each type says
exactly how its parameters are checked.

---

## Part 3: the simulator's limits

The simulator's values follow the orbit: battery voltage from 7.10 to 7.70 V, temperature from −5.0 to 35.0 °C.
The limits are chosen so that every orbit shows both levels:

| Parameter | Warning range | Critical range | On-board FDIR | What an orbit shows |
|---|---|---|---|---|
| `BATTERY_VOLTAGE` | 7.20 … 7.80 V | 7.12 … 7.90 V | `BATTERY_LOW` below 7.15 V | warning at 7.199 V, critical at 7.119 V |
| `PLATFORM_TEMPERATURE` | up to 32.0 °C | up to 34.5 °C | `TEMPERATURE_HIGH` above 33.0 °C | warning at 32.1 °C, critical at 34.6 °C |

Temperature has only an upper limit because the simulator never gets cold enough to matter. A real spacecraft
would have limits on both sides.

For the battery, the order in time is: ground warning (7.20 V), then FDIR acts (7.15 V), then ground critical
(7.12 V). This is the arrangement Part 0 described: the operator is warned before the spacecraft acts.

---

## Part 4: when the check happens

Values are checked **at ingest**, once, in the transaction that stores them:

```
TelemetryReception.receive                               (one transaction)
  1. archive the raw packet
  2. decode with the mission database
  3. store the values and publish "values received"
       └─ alarm module, same transaction:
          4. check every value whose type has limits     → IN_LIMITS / WARNING / CRITICAL
          5. update that parameter's alarm               → raise, escalate, mark back in limits, close
  6. log an event if the packet is TM[5,x]
```

The alarm module runs in a **nested transaction** (a JDBC savepoint inside the ingest transaction). If the alarm
update fails, only its own changes roll back and an error is logged; the packet, its values and its event are still
stored. Telemetry is never lost because of an alarm problem, at the price that such a value stays unchecked.

- Only completely decoded packets are checked, because only their values are stored.
- The time of an alarm is the packet's **generation time** (on-board time), the same time the values and events
  use. The operator reads one consistent timeline.
- Changing limits affects only new values. History is not checked again.

A check this design does **not** do is noticing **missing** telemetry. "No housekeeping for 30 s" needs a timer,
not a packet, and between passes silence is normal. It belongs with the visibility round.

---

## Part 5: the alarm lifecycle

A check only says how a single value compares with its limits. An **alarm** remembers what happened over time, so
that an operator cannot miss it. This round uses **latching** alarms: an alarm stays open until the value is back in
limits **and** an operator has acknowledged it.

```
                    value out of limits
   (no alarm) ─────────────────────────────► TRIGGERED
                                              │     │
                     operator acknowledges    │     │  value back in limits
                ┌─────────────────────────────┘     └──────────────────────┐
                ▼                                                          ▼
          ACKNOWLEDGED                                                  RETURNED
                │                                                          │
                │  value back in limits             operator acknowledges  │
                └──────────────────────────► CLEARED ◄─────────────────────┘
```

| State | Value now | Acknowledged | Open |
|---|---|---|---|
| `TRIGGERED` | out of limits | no | yes |
| `ACKNOWLEDGED` | out of limits | yes | yes |
| `RETURNED` | in limits | no | yes |
| `CLEARED` | in limits | yes | no, finished |

Further rules:

- **One open alarm per parameter.** While an alarm is open, new violations update it rather than raising another.
- **Escalation.** A `WARNING` alarm whose value becomes critical is raised to `CRITICAL`. The alarm keeps the most
  severe level it has reached; it does not drop back to `WARNING` when the value improves.
  An escalated alarm needs attention again, so it **loses its acknowledgement**: `ACKNOWLEDGED` goes back to
  `TRIGGERED`.
- **Out of limits again.** A `RETURNED` alarm whose value leaves the limits again goes back to `TRIGGERED`. It is the
  same alarm, still waiting for the operator.
- **Acknowledging is idempotent.** Acknowledging an alarm that is already `ACKNOWLEDGED` changes nothing.
  Acknowledging a `CLEARED` alarm is a conflict, because it is already finished.

Why latch? In the simulator, the battery voltage rises again after the spacecraft enters SAFE mode. With alarms
that clear themselves, a battery alarm raised and cleared between two passes would leave no trace in the alarm list.
With latching, it waits as `RETURNED` until someone has seen it.

---

## Part 6: a battery dip, step by step

The housekeeping arrives at one value per second. These are the moments that matter:

| Time | `BATTERY_VOLTAGE` | Check | Alarm afterwards |
|---|---|---|---|
| 09:00:00 | 7.205 V | `IN_LIMITS` | none |
| 09:00:01 | 7.199 V | `WARNING` | #3 `TRIGGERED`, `WARNING`, trigger value 7.199 |
| 09:01:10 | 7.149 V | `WARNING` | unchanged (FDIR raises `BATTERY_LOW` and enters SAFE mode) |
| 09:02:00 | (operator acknowledges #3) | | #3 `ACKNOWLEDGED`, `WARNING` |
| 09:02:40 | 7.119 V | `CRITICAL` | #3 `TRIGGERED`, `CRITICAL` (escalated, acknowledgement dropped) |
| 09:04:30 | 7.200 V | `IN_LIMITS` | #3 `RETURNED`, `CRITICAL` |
| 09:30:00 | (operator acknowledges #3) | | #3 `CLEARED` |

The API returns open alarms, so between 09:00:01 and 09:30:00 #3 is in the list:

```json
{"alarms": [
  {"id": 3, "parameter": "BATTERY_VOLTAGE", "level": "CRITICAL", "state": "RETURNED",
   "triggeredAt": "2026-11-08T09:00:01Z", "triggerValue": 7.199,
   "returnedAt": "2026-11-08T09:04:30Z", "acknowledgedAt": null, "clearedAt": null}
]}
```

`POST /satellites/99999/alarms/3/acknowledge` at 09:30:00 returns the alarm as `CLEARED` and removes it from the
list.

---

## Part 7: how this maps to the code

| Piece | Code |
|---|---|
| `xtce` subset | `AlarmRanges(warning, critical)` with `check(double)` returning a `MonitoringLevel`; `AlarmRange` with inclusive, exclusive or open bounds; `limits()` on `IntegerParameterType` and `FloatParameterType` (`AlarmRanges.NONE` without limits); `ParameterValue.level()`; the reader rejects unsupported alarm elements |
| mission database | `simulator.xml`: new types `battery_voltage_t` and `platform_temperature_t` with the Part 3 limits; event values keep `voltage_t` and `temperature_t` |
| module boundary | a separate Spring Modulith module `alarm`. `TelemetryReception` in `tmtc` publishes a `ParameterValuesReceived` event and knows nothing about alarms; `alarm` listens with a synchronous `@EventListener`, so the check runs in the ingest transaction. Removing alarms means deleting the module and its migration |
| ground | the `alarm` module checks the values whose type has limits; the alarm transitions (raise, escalate, back in limits, acknowledge) are decided by the alarm itself, not by the code that calls it |
| archive | table `alarm` (V9): satellite, parameter, level, state, triggered at, trigger value, returned at, acknowledged at, cleared at, and a version for optimistic locking; a partial unique index allows one open alarm per parameter |
| API | `GET /satellites/{noradId}/alarms` lists open alarms; `POST /satellites/{noradId}/alarms/{id}/acknowledge`, with 404 for an unknown alarm and 409 for a cleared one, or one that new telemetry changed during the request |
| documents | requirements ALM-010 to ALM-060 in `docs/requirements/alarms.yml`, README section |

The history of cleared alarms, enumeration alarms, `minViolations`, a monitoring level stored with every value,
and checks for missing telemetry are left for later rounds.

---

## Exercises

1. `PLATFORM_TEMPERATURE` goes 31.5, 32.3, 34.7, 33.0, 31.0 °C. What does the check give at each step, and what
   is the alarm's state and level at the end?
2. In exercise 1, the operator acknowledges after 32.3 °C. What changes at 34.7 °C?
3. Why would a `WarningRange` of 7.0 … 7.8 V together with a `CriticalRange` of 7.12 … 7.90 V be rejected?
4. A database contains `<WarningRange minInclusive="7.2" maxInclusive="7.8"/>` inside a `ContextAlarmList`. Why is
   rejecting the whole database better than ignoring that element?
5. The link is down for an hour, and the battery dipped during that time. What will the alarm list show at the
   next pass?

<details>
<summary>Answers</summary>

1. 31.5 `IN_LIMITS` (no alarm), 32.3 `WARNING` (alarm `TRIGGERED`, `WARNING`), 34.7 `CRITICAL` (escalated to
   `CRITICAL`), 33.0 `WARNING` (no change; the level stays `CRITICAL`), 31.0 `IN_LIMITS` (`RETURNED`). At the end:
   `RETURNED`, `CRITICAL`, open until acknowledged.
2. After the acknowledgement the alarm is `ACKNOWLEDGED`. At 34.7 °C it escalates to `CRITICAL` and goes back to
   `TRIGGERED`, because the situation is worse than what the operator saw.
3. The warning range must lie inside the critical range. 7.0 V would be within the warning range but outside the
   critical range, which means critical but not a warning. The levels would contradict each other.
4. Ignoring it would load a database whose author expects a check that never happens, and the first sign would be
   an anomaly nobody was warned about. Rejecting it makes the problem visible when the database is loaded.
5. Nothing new. Only telemetry that reaches the ground is checked, and nothing arrived. When the spacecraft also
   downlinks recorded telemetry, that data would be checked too, which is a later topic. The `BATTERY_LOW` event,
   if it was stored on board and sent later, is what tells the story. Detecting the missing telemetry itself is the
   job of a separate check.
</details>
