# ICD: telemetry link, spacecraft simulator to ground

| | |
|---|---|
| Document | ICD-TM-001, issue 1 (2026-11-08) |
| Interface | `spacecraft_simulator` (provider) to `orbit_service` module `tmtc` (user) |
| Code | `ccsds` module, shared by both sides |
| Requirements | IF-040, IF-050, TM-010 to TM-040 (`docs/requirements/`) |

## 1. Scope

This document fixes every bit that crosses the telemetry link between the spacecraft simulator and the ground
software. In this issue the link carries telemetry only (no telecommands) and the packets travel without
transfer frames. CCSDS TM transfer frames (CCSDS 132.0-B) are planned for a later issue.

Reference documents:

- CCSDS 133.0-B-2 *Space Packet Protocol*
- CCSDS 301.0-B-4 *Time Code Formats*
- ECSS-E-ST-70-41C *Telemetry and telecommand packet utilization* (PUS-C)

## 2. Transport

| Item | Value |
|---|---|
| Protocol | TCP |
| Server | the simulator, port `10015` (`SIMULATOR_TM_PORT`) |
| Client | the ground software (`groundstation.tmtc.link.host` / `.port`) |
| Spacecraft identification | not in the packets (a space packet has no spacecraft ID); the client assigns the link a NORAD ID (`groundstation.tmtc.link.norad-id`, `99999` for the simulator) and archives every packet under it |
| Stream content | space packets back to back, with no delimiters, padding or idle data in between |
| Packet boundaries | found from the packet data length field of each primary header |
| Several clients | allowed; every connected client receives every packet |
| Packets sent while no client is connected | lost; there is no store and forward |
| Reconnection | by the client, after `groundstation.tmtc.link.reconnect-delay` (5 s) |
| Error handling | a client that can't decode a packet closes the connection and reconnects, so that it starts again at a packet boundary |

## 3. Packet layout

All multi-byte fields are big-endian (most significant byte first). Bit 0 is the most significant bit of a field.

```
┌─────────────────┬────────────────────────┬───────────────────┬───────────┐
│ primary header  │ PUS TM secondary header│     user data     │    CRC    │
│     6 bytes     │        13 bytes        │  0 to 65521 bytes │  2 bytes  │
└─────────────────┴────────────────────────┴───────────────────┴───────────┘
```

### 3.1 Primary header (CCSDS 133.0-B-2, 4.1.3)

| Field | Bits | Value on this link |
|---|---|---|
| packet version number | 3 | `0` |
| packet type | 1 | `0` (telemetry) |
| secondary header flag | 1 | `1` |
| APID | 11 | see section 4 |
| sequence flags | 2 | `0b11` (unsegmented); other values are rejected |
| packet sequence count | 14 | per APID, 0 to 16383, then wraps to 0 |
| packet data length | 16 | number of bytes after the primary header, minus 1 |

### 3.2 PUS-C TM secondary header (ECSS-E-ST-70-41C, 7.4.3.1)

| Field | Size | Value on this link |
|---|---|---|
| TM packet PUS version number | 4 bits | `2` (PUS-C) |
| spacecraft time reference status | 4 bits | `0` (on-board time synchronised) |
| service type ID | 8 bits | see section 4 |
| message subtype ID | 8 bits | see section 4 |
| message type counter | 16 bits | per service type and subtype, 0 to 65535, then wraps to 0 |
| destination ID | 16 bits | `0` (ground) |
| time | 6 bytes | see section 3.3 |

No spare field follows: the header is 13 bytes, a whole number of octets.

### 3.3 Time (CCSDS 301.0-B-4, 3.2: CUC)

| Item | Value |
|---|---|
| Code | CCSDS Unsegmented time Code, P-field implicit (not sent) |
| Coarse time | 4 bytes, unsigned, whole seconds |
| Fine time | 2 bytes, unsigned, units of 2^-16 s (about 15.3 µs) |
| Epoch | 1958-01-01T00:00:00 TAI (the CCSDS recommended epoch, level 1) |
| Time scale | TAI: no leap seconds |
| Conversion to UTC | UTC = epoch + coarse + fine − (TAI − UTC); TAI − UTC = 37 s, valid since 2017-01-01 |
| Range | until the coarse time wraps in 2094 |

Both sides use `MissionTime` in the `ccsds` module for this conversion. If a leap second is ever announced,
TAI − UTC must be updated on both sides at once.

### 3.4 Packet error control

| Item | Value |
|---|---|
| Algorithm | CRC-16-CCITT: polynomial `0x1021`, initial value `0xFFFF`, no reflection, no final XOR |
| Coverage | every byte from the start of the primary header to the end of the user data |
| Check value | `0x29B1` for the ASCII string `123456789` |
| Receiver check | the CRC over the whole packet, including the CRC field, is `0x0000` |

## 4. Packets on this link

| APID | Source | Service / subtype | Name | Period |
|---|---|---|---|---|
| 100 | platform | 3 / 25 | housekeeping parameter report | `SIMULATOR_HOUSEKEEPING_PERIOD`, 1 s by default |

### 4.1 Housekeeping parameter report, structure 1

The user data of a TM[3,25] report starts with the housekeeping parameter report structure ID, which says which
parameters follow.

| Offset | Field | Type | Unit | Range in simulation |
|---|---|---|---|---|
| 0 | housekeeping parameter report structure ID | uint8 | | `1` |
| 1 | battery voltage | uint16 | mV | 7100 to 7700 |
| 3 | platform temperature | int16, two's complement | 0.1 °C | −50 to 350 (−5.0 to 35.0 °C) |
| 5 | spacecraft mode | uint8 enum | | `0` SAFE, `1` NOMINAL |

Total: 6 bytes. In the simulator, battery voltage and temperature follow a sine over a 92-minute orbit.

## 5. Example

Housekeeping report: APID 100, sequence count 5, message type counter 1, time coarse `0x12345678` / fine
`0x8000`, and user data `01 02 03 04`. This example is for codec tests; it is not structure 1.

```
08 64 C0 05 00 12                          primary header: TM, secondary header, APID 100, unsegmented,
                                           count 5, data length 0x12 = 18 (19 bytes follow)
20 03 19 00 01 00 00 12 34 56 78 80 00     PUS-C, status 0, service 3, subtype 25, counter 1,
                                           destination 0, CUC 0x12345678.8000
01 02 03 04                                user data
FB 2C                                      CRC-16-CCITT
```

## 6. Changes

| Issue | Date | Change |
|---|---|---|
| 1 | 2026-11-08 | first issue: TM over TCP, housekeeping structure 1 |
