# Tutorial: TM transfer frames

This tutorial follows [the passes and loss of signal tutorial](passes-and-signal.md). Until now the simulator sent
space packets straight over TCP, one after another. A real downlink doesn't: packets travel inside fixed-length
**transfer frames**, and the frames travel as a continuous stream of bits from the radio. Here you will see why
frames exist, how a frame is laid out, how packets are cut up into frames and put back together, how the ground
finds frames in a stream of bytes, and how frame counters tell the ground how good the link is.

There is no code to write. The last part maps everything to the planned code.

---

## Part 0: why packets need frames

A space packet is made by an on-board application. It has whatever length its content needs, and it says nothing
about the spacecraft that sent it or the radio link it crossed. The radio needs something else:

| The radio link needs | Why | Frames give it |
|---|---|---|
| a constant stream | the receiver stays locked only while bits keep coming | fixed-length frames at a fixed rate, with idle frames when there is no data |
| units it can check | a bit flipped by noise must be noticed | a CRC over each whole frame (frame error control) |
| a way to find boundaries | a receiver that joins mid-stream, or loses bits, must find the next unit | a sync marker before every frame |
| identification | several spacecraft may share a ground station | the spacecraft ID in every frame header |
| priorities | an alarm shouldn't wait behind routine data | virtual channels, each with its own queue |
| a way to measure loss | operators want to know how much was lost, and where | frame counters per spacecraft and per channel |

The layers stack like this:

```
 on-board applications      packets (any length)                 CCSDS 133.0-B, PUS
          │
 frame generator            TM transfer frames (fixed length)    CCSDS 132.0-B
          │
 synchronisation and        randomized frame + sync marker       CCSDS 131.0-B
 channel coding             ("CADU")
          │
 radio                      bits
```

In this project the radio is TCP. The simulator sends CADUs back to back, just as a demodulator would hand them
to the ground software after a real radio.

---

## Part 1: the frame layout

Every frame on this link is **64 bytes**:

```
┌──────────────────┬──────────────────────────────────────────┬──────────┐
│  primary header  │               data field                 │   FECF   │
│     6 bytes      │                56 bytes                  │ 2 bytes  │
└──────────────────┴──────────────────────────────────────────┴──────────┘
```

The primary header, bit by bit:

| Bits | Field | Value on this link |
|---|---|---|
| 2 | transfer frame version | `00` (TM frame) |
| 10 | spacecraft ID | `42` |
| 3 | virtual channel ID | `0` housekeeping, `1` events, `7` idle |
| 1 | operational control field flag | `0`: no OCF (it carries telecommand reports, and this link has no telecommands) |
| 8 | master channel frame count | +1 for every frame of the spacecraft, wraps at 256 |
| 8 | virtual channel frame count | +1 for every frame of that virtual channel, wraps at 256 |
| 1 | secondary header flag | `0` |
| 1 | synchronisation flag | `0`: the data field holds packets |
| 1 | packet order flag | `0` |
| 2 | segment length ID | `11` (required when the synchronisation flag is 0) |
| 11 | first header pointer | where the first packet starts in the data field (Part 2) |

The **frame error control field** (FECF) is a CRC-16-CCITT over the other 62 bytes, the same CRC the packets
already use. A frame whose FECF doesn't match is thrown away whole.

The sizes are a mission choice. 64 bytes is small for a real mission; it was chosen here because the simulator
sends only about 27 bytes of housekeeping per second. With 64-byte frames, packets regularly span two frames, which
is the part of the decoder that most needs testing.

Not every byte is data:

| Per frame | Bytes |
|---|---|
| sync marker | 4 |
| header | 6 |
| FECF | 2 |
| data field | 56 |
| **on the wire** | **68**, of which 82 % is data field |

---

## Part 2: packets inside frames

Each virtual channel turns its packets into one continuous stream of bytes and cuts that stream into 56-byte data
fields. A packet that doesn't fit in the rest of a frame continues in the next frame **of the same virtual
channel**.

The **first header pointer** (FHP) says where the first packet that **starts** in this frame begins:

```
 VC0 frame n                                    VC0 frame n+1
 ┌───────────────────────────────────────────┐  ┌───────────────────────────────────────────┐
 │ HK #1 (27) │ HK #2 (27) │ HK #3 first 2    │  │ HK #3 last 25 │ HK #4 (27) │ HK #5 first 4 │
 └───────────────────────────────────────────┘  └───────────────────────────────────────────┘
 FHP = 0                                         FHP = 25
```

| FHP | Meaning |
|---|---|
| 0 to 2045 | a packet starts at this offset; the bytes before it finish the packet from the previous frame |
| `0x7FF` (2047) | no packet starts in this frame: the whole data field continues a long packet |
| `0x7FE` (2046) | the frame holds only idle data |

The FHP is what lets the ground **recover after a lost frame**. If frame n is lost, the ground can't finish HK #3,
because it never saw the start. It throws away the 25 leftover bytes and starts again at offset 25 of frame n+1,
where HK #4 begins. One lost frame costs the packets it carried, not the rest of the pass.

### Two virtual channels, two release policies

| Channel | Content | When a frame is sent |
|---|---|---|
| VC0 | housekeeping | only when a full 56-byte data field is waiting: no space is wasted, but a housekeeping packet can wait up to about 2 seconds |
| VC1 | event reports | at the next frame slot, even if the frame is mostly empty: an event waits at most a quarter of a second |
| VC7 | idle frames | when neither VC0 nor VC1 has anything to send |

A VC1 frame that isn't full is padded with an **idle packet**: a packet with APID `0x7FF` (all ones), whose content
means nothing. The ground recognises the APID and drops it. An idle packet needs at least 7 bytes (a 6-byte header
and 1 byte of data). If less space is left in the frame, the idle packet continues into the next VC1 frame, which
is then sent too.

The frame slots come four times a second. In each slot the simulator sends one frame, chosen in this order: a VC1
frame if an event is waiting, otherwise a full VC0 frame if one is ready, otherwise an idle frame. Because idle
frames fill every gap, the frame rate is constant: 4 frames per second, 272 bytes per second on the wire.

The packet sequence count and the frame counts are unrelated. A packet's sequence count belongs to its application
(APID). A frame count belongs to the link.

---

## Part 3: finding frames in a stream

### The attached sync marker

Every frame is preceded by the 4-byte **attached sync marker** (ASM) `1A CF FC 1D`. A frame with its marker is a
**CADU** (channel access data unit):

```
 1A CF FC 1D │ 64-byte randomized frame │ 1A CF FC 1D │ 64-byte randomized frame │ ...
```

The ground reads the stream like this:

```
search:  read until the last 4 bytes are 1A CF FC 1D
frame:   read the next 64 bytes, derandomize them, check the FECF
         then expect the next marker right after the frame;
         if it isn't there, go back to search
```

Before, a single bad byte forced the ground to close the TCP connection and reconnect, because there was no other
way to find the next packet boundary. Now the ground simply searches for the next marker, and nothing else is lost.

### Why randomize?

A radio receiver recovers its clock from the changes between 0 and 1 in the signal. A frame full of zeros, such
as idle data or an empty field, has no changes, and the receiver drifts out of lock. The **pseudo-randomizer**
(CCSDS 131.0-B) prevents that: it XORs the frame with a fixed pseudo-random sequence that starts again with every
frame.

| Item | Value |
|---|---|
| generator polynomial | x⁸ + x⁷ + x⁵ + x³ + 1 |
| initial state | all ones |
| sequence | repeats every 255 bits; starts `FF 48 0E C0 9A 0D 70 BC ...` |
| applied to | the 64-byte frame, after the FECF is computed; not to the sync marker |

XOR twice with the same sequence gives back the original, so derandomizing is the same operation as randomizing.
Randomizing also makes it much less likely that the frame content looks like the sync marker by accident.

---

## Part 4: measuring the link

### Counting lost frames

The **master channel frame count** goes up by one for every frame of the spacecraft, idle frames included. The
ground remembers the last count it received and expects the next one:

```
received 17, 18, 19, 23, 24
                   └── 20, 21, 22 are missing: 3 frames lost
```

The count wraps after 255, so the gap is computed modulo 256: after 254, a count of 1 means 2 frames lost (255 and
0). At 4 frames per second the count wraps every 64 seconds. A gap longer than that can't be measured, so the
ground counts gaps only when the previous frame arrived less than 30 seconds earlier. After a longer break, such as
the time between two passes, it starts counting afresh.

A frame whose FECF fails is thrown away before its header is read, because a header that fails the check can't be
trusted. It shows up later as part of a gap. The ground counts it separately as well:

| Count | Meaning |
|---|---|
| frames received | frames that passed the check |
| frames lost | missing from the counter sequence: corrupted, or never received |
| frames failing the check | received but corrupted; part of frames lost |
| idle frames | received frames on VC7 |

Lost minus failing the check is roughly how many frames never arrived at all.

### Link reports

The counts are kept **per pass**, as a **link report**, the summary an operator reads after a contact:

```json
{"passStart": "2026-11-08T18:02:27.7Z", "passEnd": "2026-11-08T18:10:07.1Z",
 "framesReceived": 1712, "framesLost": 128, "framesFailedCheck": 41, "idleFrames": 1301,
 "lossRatio": 0.0696, "updatedAt": "2026-11-08T18:10:07.1Z"}
```

`lossRatio` is lost / (received + lost). The report of the current pass is written every 10 seconds and once more
when the pass ends, so `GET /satellites/{noradId}/link-reports` shows a pass while it is still going on. Frames
received outside a planned pass, or while no pass can be predicted, are not in any report.

### The link-quality alarm

When more than **5 %** of the frames of the current pass are lost
(`groundstation.contact.max-frame-loss`), the ground raises a `LINK_QUALITY` warning, a new alarm kind next to
`LIMIT` and `LOSS_OF_SIGNAL`. It is judged only once at least 100 frames of the pass have been expected (25
seconds), so that two bad frames in the first second don't raise it.

| Event | Alarm |
|---|---|
| loss ratio of the pass rises above 5 % | raised as `TRIGGERED`, `WARNING` |
| loss ratio falls back to 5 % or less | `RETURNED`, or `CLEARED` if acknowledged |
| the pass ends | `RETURNED`, or `CLEARED` if acknowledged |
| the ratio rises above 5 % again, alarm still `RETURNED` | `TRIGGERED` again |

Loss of signal and link quality complement each other. Loss of signal means nothing arrives. Link quality means
something arrives, but not enough.

---

## Part 5: damaging the link on purpose

A link that never fails can't show any of this. The simulator can damage frames after it has made them:

| Environment variable | Default | Effect |
|---|---|---|
| `SIMULATOR_FRAME_DROP_RATE` | `0` | share of frames not sent at all, 0 to 1 |
| `SIMULATOR_FRAME_CORRUPT_RATE` | `0` | share of frames sent with one bit flipped, 0 to 1 |

A dropped frame shows up only as a gap in the counter. A corrupted frame fails the FECF and also shows up as a
gap. With a drop rate of 0.05 and a corrupt rate of 0.03 the loss ratio of a pass settles near 8 %, so the
link-quality alarm is raised.

---

## Part 6: one frame, byte by byte

A VC0 frame with master channel frame count 20 and VC0 frame count 5 (the 21st and 6th frames, since counts
start at 0), whose first packet starts at offset 25:

```
02 A0 14 05 18 19 ...  (56 data bytes) ...  CRC
└─┬─┘ │  │  └─┬─┘
  │   │  │    └──── data field status: flags 0 0 0, segment length ID 11, FHP 25
  │   │  └───────── virtual channel frame count 5
  │   └──────────── master channel frame count 20
  └──────────────── version, spacecraft ID, virtual channel ID, OCF flag
```

`02 A0` is `0000 0010 1010 0000`: version `00`, spacecraft ID `0000101010` (42), virtual channel ID `000`, OCF
flag `0`. `18 19` is `0001 1000 0001 1001`: secondary header, synchronisation and packet order flags `0 0 0`,
segment length ID `11`, then the FHP `000 0001 1001`, which is 25.

On the wire the ground receives `1A CF FC 1D` followed by these 64 bytes XORed with `FF 48 0E C0 9A 0D ...`, so the
frame bytes on the wire start `FD E8 1A C5 82 14 ...`.

---

## Part 7: how this maps to the code

| Piece | Code |
|---|---|
| frame | `TmFrame` in `ccsds`: header fields, data field, encode with FECF, decode with FECF check |
| randomizer | `Randomizer` in `ccsds`, the CCSDS 131.0-B sequence |
| sync | `FrameSynchronizer` in `ccsds`: finds the marker, reads and derandomizes frames, searches again when the marker is missing |
| packets into frames | `VirtualChannelFramer` in `ccsds`: per channel, a byte queue cut into data fields, with the full-frame and next-slot release policies and idle packets |
| frames into packets | `PacketExtractor` in `ccsds`: per channel, joins packets across frames, recovers at the FHP after a gap, drops idle packets |
| simulator | 4 frame slots per second; VC1, then full VC0, then idle; drop and corrupt rates; queues are emptied while out of view |
| ground link | `TmLink` reads CADUs; frames of another spacecraft are dropped; frame counts go to `contact`, packets to `TelemetryReception` as before |
| link reports | in `contact`: per pass counts, a new table `link_report`, `GET /satellites/{noradId}/link-reports` |
| alarm | new kind `LINK_QUALITY`; `contact` publishes link degraded and link recovered |
| interface | ICD issue 4: frames, sync marker and randomizer replace the bare packets |
| documents | new interface, telemetry, contact and alarm requirements; TM-030 changes, because a bad frame no longer closes the connection |

Reed-Solomon or convolutional coding (which corrects errors instead of only detecting them), the operational
control field, frame secondary headers and recorded telemetry on its own virtual channel are left for later rounds.

---

## Exercises

1. A VC0 frame has FHP 40. What do bytes 0 to 39 of its data field hold, and what does the ground do with them if
   the previous VC0 frame was lost?
2. The ground receives master channel frame counts 250, 251, 3, 4. How many frames were lost?
3. Why does a failed FECF make the ground throw away the whole frame, rather than only the packets whose bytes
   look wrong?
4. Housekeeping arrives on the ground in bursts of two packets about every two seconds, not one per second. Why,
   and why are events not delayed the same way?
5. Without randomization, which frames on this link would be hardest for a real receiver, and why?
6. A pass has 1600 frames received and 70 lost. Is the link-quality alarm raised?

<details>
<summary>Answers</summary>

1. The last 40 bytes of a packet that started in the previous VC0 frame. If that frame was lost, the start of the
   packet is missing, so the ground drops the 40 bytes and starts reading the first complete packet at offset 40.
2. Counts 252, 253, 254, 255, 0, 1 and 2 are missing: 7 frames.
3. The FECF only says that some bit in the frame is wrong, not which one. The wrong bit could be in the header, so
   even the frame counts, the channel and the FHP can't be trusted.
4. VC0 sends a frame only when its 56-byte data field is full, and two 27-byte packets fill it in about two
   seconds. VC1 sends at the next slot and pads with an idle packet, so an event waits at most a quarter of a
   second. It's the trade between efficiency and latency.
5. Idle frames. Their data is a constant pattern, so the signal would have long runs without changes between 0 and
   1, and the receiver couldn't recover its clock. They are most of the frames on this link.
6. No: 70 / (1600 + 70) = 4.2 %, which is below 5 %. With 1600 frames received it would be raised at 85 lost:
   85 / 1685 = 5.04 %.
</details>
