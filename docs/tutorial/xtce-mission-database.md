# Tutorial: the mission database and XTCE

This tutorial follows [the codec tutorial](ccsds-codec.md). There you turned bytes into a `TmPacket`:
headers you can read, and a `userData` array you can't. This tutorial is about that array: how a ground
segment learns what the bytes mean, from a file instead of from Java code.

No code to write here. By the end you should be able to read an XTCE file, decode a housekeeping packet by
hand with it, and recognise every piece of the code that will implement it.

---

## Part 0: why a mission database

### The problem

Today the backend archives this packet from the simulator, received on 2026-11-08:

```
0864C0210014200319002100008182973AEC54011CF3009E01ACC1
```

The codec can tell you that it is APID 100, service 3/25, sequence count 33. It can't tell you that the
battery is at 7.411 V. That knowledge is in the [ICD](../icd/tm-link.md), section 4.1, a table written for
people.

You could write it in Java:

```java
var battery = buffer.getShort(20) & 0xFFFF;
var volts = battery * 0.001;
```

and it would work, for one packet. A real spacecraft has hundreds of packet layouts and thousands of
parameters, and they change during the mission: a new software version on board adds a field, a sensor is
recalibrated. Each change would mean a new release of the ground software.

### The solution

Describe the layouts as **data**, in a file, and write one generic decoder that reads any layout. That file is
the **mission database** (MDB). In ESA's world it is also called the *satellite database* or *SDB*.

```
           packet bytes                            parameter values
TM link  ──────────────►  generic decoder  ──────────────────────────►  parameter archive  ──►  /parameters
                                ▲
                                │ "byte 20, uint16, × 0.001, V"
                         mission database (XTCE file)
```

Changing a calibration then means editing the file, not the code. The same file can drive several tools: the
decoder, the displays, the limit checks, and even the on-board software team's documentation.

### What XTCE is

**XTCE** (XML Telemetric and Command Exchange) is a standard for writing a mission database. It is published by
the OMG (Object Management Group, also known for UML) and adopted by CCSDS as a recommended standard. The
version in use is **XTCE 1.2** (2018), namespace `http://www.omg.org/spec/XTCE/20180204`.

Who uses it: Yamcs reads it as its native format, NASA and ESA projects use it to exchange databases between
organisations, and many CubeSat teams write their database in it so that they can use Yamcs.

XTCE is big: it describes telemetry, commands, verification, algorithms and streams. We implement a **subset**,
listed in Part 6, and reject everything else.

---

## Part 1: the four building blocks

An XTCE file for telemetry has one root and three lists:

```xml
<SpaceSystem name="SIMULATOR" xmlns="http://www.omg.org/spec/XTCE/20180204">
  <TelemetryMetaData>
    <ParameterTypeSet>  …  </ParameterTypeSet>   <!-- HOW a value is encoded and calibrated -->
    <ParameterSet>      …  </ParameterSet>       <!-- WHICH values exist, by name -->
    <ContainerSet>      …  </ContainerSet>       <!-- WHERE they are in a packet -->
  </TelemetryMetaData>
</SpaceSystem>
```

| Block | Question it answers | Java analogy |
|---|---|---|
| `SpaceSystem` | which spacecraft (or subsystem) this describes | a package |
| parameter type | how many bits, signed or not, how to turn the raw number into a real value, which unit | a class |
| parameter | a named value, such as `BATTERY_VOLTAGE` | a field, declared with its type |
| container | the order of parameters in a packet, and when this layout applies | a record layout, with inheritance |

Types and parameters are separate so that many parameters can share one type: ten temperature sensors, one
`temperature_t`.

Space systems can be nested (`SIMULATOR/POWER/BATTERY_VOLTAGE`), which large missions use to give each
subsystem team its own part. We use a single level.

---

## Part 2: from bits to values

Every parameter has three forms. The vocabulary matters, because the API will expose two of them:

| Form | Example | What it is |
|---|---|---|
| bits on the wire | `1C F3` | what the codec hands over |
| **raw value** | `7411` | the bits read as a number, following the **encoding** |
| **engineering value** | `7.411 V` | the raw value after **calibration** |

### The encoding: bits to raw value

```xml
<IntegerDataEncoding sizeInBits="16" encoding="unsigned"/>
```

`sizeInBits` says how many bits to read (default 8). `encoding` says how to read them:

| `encoding` | 16 bits `FF CE` read as | Used for |
|---|---|---|
| `unsigned` (default) | 65486 | counters, voltages, anything that can't be negative |
| `twosComplement` | −50 | signed sensor values; what Java's `short` does |
| `signMagnitude`, `onesComplement`, `BCD`, `packedBCD` | — | older hardware; not in our subset |

Bytes are big-endian by default (`byteOrder="mostSignificantByteFirst"`), like everything in CCSDS.

Two's complement by hand, as in [byte mechanics](byte-mechanics.md) section 2: if the top bit is set, subtract
2^sizeInBits. `0xFFCE` = 65486; 65486 − 65536 = **−50**.

### The calibration: raw value to engineering value

The most common calibrator is a polynomial:

```xml
<DefaultCalibrator>
  <PolynomialCalibrator>
    <Term exponent="0" coefficient="-273.15"/>
    <Term exponent="1" coefficient="0.01"/>
  </PolynomialCalibrator>
</DefaultCalibrator>
```

Engineering value = Σ coefficient × raw^exponent. This example turns a raw value in units of 0.01 K into
°C: raw 29815 → −273.15 + 298.15 = 25.0 °C. Missing terms count as 0.

Our simulator needs only the linear term:

| Parameter | Raw | Polynomial | Engineering |
|---|---|---|---|
| battery voltage | 7411 | 0.001 × raw | 7.411 V |
| temperature | 158 | 0.1 × raw | 15.8 °C |

Other calibrators exist: `SplineCalibrator` (a lookup curve, typical for thermistors) and `MathOperationCalibrator`
(an arbitrary formula). Not in our subset.

### The type: putting it together

```xml
<FloatParameterType name="voltage_t">
  <UnitSet><Unit>V</Unit></UnitSet>
  <IntegerDataEncoding sizeInBits="16" encoding="unsigned">
    <DefaultCalibrator>
      <PolynomialCalibrator><Term exponent="1" coefficient="0.001"/></PolynomialCalibrator>
    </DefaultCalibrator>
  </IntegerDataEncoding>
</FloatParameterType>
```

Read it as: *"on the wire it's a 16-bit unsigned integer; multiply by 0.001 and the result is a floating-point
number in volts."* The element name (`FloatParameterType`) gives the type of the **engineering** value; the
encoding inside gives the type on the **wire**. That's why a float type can contain an *integer* encoding.

For values that stay integers, use `IntegerParameterType`:

```xml
<IntegerParameterType name="uint8_t" signed="false">
  <UnitSet/>
  <IntegerDataEncoding sizeInBits="8"/>
</IntegerParameterType>
```

### Enumerations: raw number to a name

```xml
<EnumeratedParameterType name="mode_t">
  <UnitSet/>
  <IntegerDataEncoding sizeInBits="8"/>
  <EnumerationList>
    <Enumeration value="0" label="SAFE"/>
    <Enumeration value="1" label="NOMINAL"/>
  </EnumerationList>
</EnumeratedParameterType>
```

Raw value 1, engineering value `NOMINAL`. A raw value with no label (say 7) is a real event in operations: either
the database is out of date or something on board is wrong. The decoder must keep the raw value and flag it,
not throw it away.

### The parameters

```xml
<ParameterSet>
  <Parameter name="BATTERY_VOLTAGE"      parameterTypeRef="voltage_t"/>
  <Parameter name="PLATFORM_TEMPERATURE" parameterTypeRef="temperature_t"/>
  <Parameter name="SPACECRAFT_MODE"      parameterTypeRef="mode_t"/>
</ParameterSet>
```

References are by name, as strings. A reader has to resolve them, and reject a file where a name points to
nothing.

---

## Part 3: containers, or where things are

### A container is an ordered list of entries

```xml
<SequenceContainer name="HK_STRUCTURE_1">
  <EntryList>
    <ParameterRefEntry parameterRef="BATTERY_VOLTAGE"/>
    <ParameterRefEntry parameterRef="PLATFORM_TEMPERATURE"/>
    <ParameterRefEntry parameterRef="SPACECRAFT_MODE"/>
  </EntryList>
</SequenceContainer>
```

No offsets are written down. Each entry starts where the previous one ended, and its size comes from its type's
encoding: 16 + 16 + 8 bits. (XTCE can also place an entry at an explicit bit position with
`LocationInContainerInBits`; we won't need it.)

### Inheritance: the same header every time

Every packet starts with the same 6-byte CCSDS primary header, and every PUS telemetry packet continues with the
same 13-byte secondary header. Repeating them in every container would be painful, so XTCE has inheritance:

```xml
<SequenceContainer name="HK_STRUCTURE_1">
  <EntryList> … the three parameters … </EntryList>
  <BaseContainer containerRef="PUS_HK_REPORT"> … </BaseContainer>
</SequenceContainer>
```

A container with a `BaseContainer` **starts with all the entries of its base**, then continues with its own.
Bases can have bases. For our packet the chain is:

```
CCSDS_PACKET            version, type, sec. header flag, APID, sequence flags, count, length   (48 bits)
  └─ PUS_TM             PUS version, time status, service, subtype, counter, destination, time (104 bits)
       └─ PUS_HK_REPORT housekeeping structure ID                                              (8 bits)
            └─ HK_STRUCTURE_1   battery voltage, temperature, mode                              (40 bits)
```

The first three levels are the same for every PUS mission. Only the last level is specific to your spacecraft.

### Restriction criteria: when a layout applies

A packet doesn't say "I am HK_STRUCTURE_1". The decoder has to work it out from values it has already read. Each
`BaseContainer` therefore carries a condition:

```xml
<SequenceContainer name="PUS_HK_REPORT" abstract="true">
  <EntryList>
    <ParameterRefEntry parameterRef="HK_STRUCTURE_ID"/>
  </EntryList>
  <BaseContainer containerRef="PUS_TM">
    <RestrictionCriteria>
      <ComparisonList>
        <Comparison parameterRef="SERVICE_TYPE" value="3"/>
        <Comparison parameterRef="MESSAGE_SUBTYPE" value="25"/>
      </ComparisonList>
    </RestrictionCriteria>
  </BaseContainer>
</SequenceContainer>

<SequenceContainer name="HK_STRUCTURE_1">
  <EntryList> … </EntryList>
  <BaseContainer containerRef="PUS_HK_REPORT">
    <RestrictionCriteria>
      <ComparisonList>
        <Comparison parameterRef="APID" value="100"/>
        <Comparison parameterRef="HK_STRUCTURE_ID" value="1"/>
      </ComparisonList>
    </RestrictionCriteria>
  </BaseContainer>
</SequenceContainer>
```

Read the second one as: *"HK_STRUCTURE_1 is a PUS_HK_REPORT whose APID is 100 and whose structure ID is 1."*

- A `ComparisonList` is an AND of its comparisons. A single condition can use `Comparison` on its own.
- `comparisonOperator` defaults to `==`; others are `!=`, `<`, `<=`, `>`, `>=`.
- The comparison uses the **engineering** value by default (`useCalibratedValue="true"`). For integers without a
  calibrator, raw and engineering are the same. For an enumeration, compare against the label, or set
  `useCalibratedValue="false"` to compare the raw number.
- `abstract="true"` means the container is never a complete packet on its own. A decoder that ends on an abstract
  container has found a packet it doesn't fully know.

Why is APID 100 on the last level and not on `PUS_HK_REPORT`? In PUS, structure IDs are defined **per
application**: APID 100's structure 1 and APID 200's structure 1 can be completely different reports.

### The decoding algorithm

With those rules, a generic decoder needs no knowledge of any mission:

```
container := the root (CCSDS_PACKET)
position  := 0
loop:
    decode every entry of container, from position onwards; remember each value by name
    children := containers whose base is container and whose restriction matches the values so far
    if no child:      stop; container is the packet's most specific layout
    if several:       error; the database is ambiguous
    container := the only child
```

Matching is done top-down, so a restriction can only refer to parameters that are already decoded: a parent's
entries, or a grandparent's. The order in the file doesn't matter.

---

## Part 4: decoding the real packet by hand

The packet from Part 0, 27 bytes:

```
08 64 C0 21 00 14 | 20 03 19 00 21 00 00 81 82 97 3A EC 54 | 01 | 1C F3 00 9E 01 | AC C1
```

**Level 1, `CCSDS_PACKET`, bytes 0–5.** Same fields as the codec, now as named parameters:

| Parameter | Bits | Raw |
|---|---|---|
| `PACKET_VERSION` | `000` | 0 |
| `PACKET_TYPE` | `0` | 0 (TM) |
| `SECONDARY_HEADER_FLAG` | `1` | 1 |
| `APID` | `000 0110 0100` | **100** |
| `SEQUENCE_FLAGS` | `11` | 3 |
| `SEQUENCE_COUNT` | `00 0000 0010 0001` | 33 |
| `PACKET_DATA_LENGTH` | `0x0014` | 20 |

Children of `CCSDS_PACKET`: `PUS_TM`, restricted to `SECONDARY_HEADER_FLAG == 1` and `PACKET_TYPE == 0`. Both match.

**Level 2, `PUS_TM`, bytes 6–18.**

| Parameter | Bytes | Raw |
|---|---|---|
| `PUS_VERSION` / `TIME_REFERENCE_STATUS` | `20` | 2 / 0 |
| `SERVICE_TYPE` | `03` | **3** |
| `MESSAGE_SUBTYPE` | `19` | **25** |
| `MESSAGE_TYPE_COUNTER` | `00 21` | 33 |
| `DESTINATION_ID` | `00 00` | 0 |
| `TIME_COARSE` / `TIME_FINE` | `81 82 97 3A` / `EC 54` | 2172819258 / 60500 |

Children of `PUS_TM`: `PUS_HK_REPORT` (service 3, subtype 25). Matches.

**Level 3, `PUS_HK_REPORT`, byte 19.** `HK_STRUCTURE_ID` = `01` = **1**.

Children: `HK_STRUCTURE_1` (APID 100, structure 1). Matches.

**Level 4, `HK_STRUCTURE_1`, bytes 20–24.**

| Parameter | Bytes | Encoding | Raw | Calibration | Engineering |
|---|---|---|---|---|---|
| `BATTERY_VOLTAGE` | `1C F3` | uint16 | 7411 | × 0.001 | **7.411 V** |
| `PLATFORM_TEMPERATURE` | `00 9E` | int16 | 158 | × 0.1 | **15.8 °C** |
| `SPACECRAFT_MODE` | `01` | uint8, enumeration | 1 | label | **NOMINAL** |

No children: `HK_STRUCTURE_1` is the packet's layout. Bytes 25–26 (`AC C1`) are the CRC, which the codec already
checked; the database doesn't describe it.

The time fields: 2172819258 s after 1958-01-01 TAI is 2026-11-08T08:53:41 UTC, as `MissionTime` computes (2026-11-08
midnight is 2172787237, plus 32021 s = 8 h 53 min 41 s). The fine time 60500 / 65536 = 0.923 s. Each
parameter value is stored with this **generation time**, so you can plot battery voltage against the time it was
measured, not the time it reached the ground.

---

## Part 5: what goes in the archive

Decoding at ingest (the decision for this round) turns one packet into one row per parameter:

| satellite | parameter | generation time | raw | engineering | packet |
|---|---|---|---|---|---|
| 99999 | `BATTERY_VOLTAGE` | 08:53:41.923 | 7411 | 7.411 | → `tm_packet` 12 |
| 99999 | `PLATFORM_TEMPERATURE` | 08:53:41.923 | 158 | 15.8 | → `tm_packet` 12 |
| 99999 | `SPACECRAFT_MODE` | 08:53:41.923 | 1 | NOMINAL | → `tm_packet` 12 |

Header fields (APID, counters, time) are decoded too, since the algorithm needs them. The implementation stores
them as parameters as well, like every other decoded value: the decoder stays generic, with no list of "fields
not worth keeping", and `APID` or `SEQUENCE_COUNT` can be queried like any parameter. The price is 19 rows per
packet instead of 3.

Why keep both raw and engineering values? If a calibration turns out to be wrong, the raw values let you
recompute history; and an operator investigating an anomaly often wants to see what the spacecraft actually sent.
Real archives keep both for this reason.

At one housekeeping packet per second, 19 parameters is about 1.6 million rows a day per satellite. That is fine
for PostgreSQL with the right index (satellite, parameter, archive order) for a while, but it grows quickly; it is
the reason real systems like Yamcs use their own time-series storage for parameters, and why a retention policy
will be needed later.

---

## Part 6: how this maps to the code

| XTCE concept | Code |
|---|---|
| the file | `orbit_service/src/main/resources/mdb/simulator.xml`, assigned to the TM link's satellite in the configuration (`groundstation.tmtc.link.mission-database`) |
| official schema | a copy of the XTCE 1.2 XSD in the `xtce` project; every file is validated against it on startup with the JDK (`javax.xml.validation`), without network access |
| reading | `XtceReader`: parses with DOCTYPE declarations refused, validates, then builds the model and fails loudly on unsupported elements or unresolved names |
| parameter type, encoding, calibrator | records: `IntegerParameterType`, `FloatParameterType`, `EnumeratedParameterType`, `IntegerEncoding`, `PolynomialCalibrator` |
| engineering values | `IntegerValue`, `FloatValue`, `EnumeratedValue`; an unlabelled enumeration value has none |
| container tree | `SequenceContainer` with resolved references: each container knows its entries and its children with their restrictions (`Comparison`) |
| decoding algorithm | `MissionDatabase.decode(bytes)`, returning the container it ended in and the parameter values; the CRC is cut off before decoding |
| archive | table `parameter_value` (raw, engineering number or label, generation time, source packet), filled by `TelemetryReception` in the same transaction as the packet |
| API | `GET /satellites/{noradId}/parameters` (what exists) and `/parameters/{name}/values?from=&to=&limit=&before=` (history) |

The supported subset, so you know what to expect from the reader:

- `IntegerParameterType`, `FloatParameterType`, `EnumeratedParameterType`
- `IntegerDataEncoding` with `unsigned` or `twosComplement`, big-endian, 1–32 bits
- `PolynomialCalibrator` as the `DefaultCalibrator` of a `FloatParameterType`
- `SequenceContainer` with `ParameterRefEntry`, `BaseContainer`, `abstract`; exactly one root container
- `RestrictionCriteria` with `Comparison` or `ComparisonList`, operator `==`
- `LongDescription`, `AliasSet`, `AncillaryDataSet` and `Header` are accepted and ignored

Everything else, such as `FloatDataEncoding`, string types, explicit locations or spline calibrators, is rejected
with a message naming the element. Duplicate names never reach the reader: the XTCE schema itself declares them
as keys, so validation rejects them.

---

## Exercises

1. A packet from APID 100 has user data `01 1D 4C FF CE 00`. Give every parameter's raw and engineering value.
2. Which container does a packet with APID 100, service 3/25 and structure ID **2** end in? What should the
   decoder report?
3. Write the type and the parameter for a new value: solar array current, uint16 in mA, engineering unit A.
4. Why doesn't `HK_STRUCTURE_1` list the CRC?
5. A colleague adds `HK_STRUCTURE_1B` with the same restriction as `HK_STRUCTURE_1`. What happens when the next
   packet arrives?
6. The spacecraft sends mode 2, which isn't in `mode_t`. What should be stored?

<details>
<summary>Answers</summary>

1. Structure ID 1. Battery `1D 4C` = 7500 → 7.5 V. Temperature `FF CE` as two's complement = 65486 − 65536 = −50 →
   −5.0 °C. Mode `00` → SAFE.
2. `PUS_HK_REPORT`: no child matches. It is abstract, so the decoder should report an unknown housekeeping
   structure (APID 100, structure 2), keep the raw packet (it is already in `tm_packet`) and store no parameter
   values. Usually this means the database is older than the on-board software.
3. `<FloatParameterType name="current_t"><UnitSet><Unit>A</Unit></UnitSet><IntegerDataEncoding sizeInBits="16">`
   `<DefaultCalibrator><PolynomialCalibrator><Term exponent="1" coefficient="0.001"/></PolynomialCalibrator>`
   `</DefaultCalibrator></IntegerDataEncoding></FloatParameterType>` and
   `<Parameter name="SOLAR_ARRAY_CURRENT" parameterTypeRef="current_t"/>`, plus an entry in a container.
4. The codec has already checked it and it carries no information about the spacecraft. Some databases describe it
   anyway, as a parameter that is decoded and ignored; it is a matter of taste.
5. Two children match, so the decoder stops with an "ambiguous database" error. Better: the reader could detect
   identical restrictions on startup, before any packet arrives.
6. Raw value 2, and an engineering value marked as invalid or unknown, so the operator sees that something is
   off. Dropping the value would hide exactly the kind of anomaly operators are paid to notice.
</details>

---

## Further reading (free)

- OMG, *XML Telemetric and Command Exchange (XTCE) 1.2*: the specification and the XSD, at omg.org/spec/XTCE
- CCSDS 660.1-G, *XTCE: Element Description* (green book): a more readable guide to the elements
- Yamcs documentation, section *Mission Database*: how a real system uses XTCE, with many examples
