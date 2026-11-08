# Tutorial: writing a CCSDS / PUS telemetry codec

This tutorial assumes you know Java well and know nothing about space. By the end you will
have written, test-first, the `ccsds` module: the code that turns a satellite's telemetry
bytes into Java objects and back.

You write all the code. The tutorial gives you the background, the byte layouts, the tests
to write, and hints. Where a hint is a near-solution, it is folded away in a
`<details>` block so you can try first.

Work in `ccsds/src/main/java/lt/vsh1ft/ccsds` and `ccsds/src/test/java/lt/vsh1ft/ccsds`.
Run `./gradlew :ccsds:check` after every step. The coverage report is at
`ccsds/build/reports/jacoco/test/html/index.html`, and the gate wants 100 % lines and branches.

---

## Part 0: the big picture

### What a satellite sends

A satellite's on-board computer keeps measuring things: battery voltage, temperatures,
which mode it is in. Every few seconds it packs those values into a small binary message
and sends it to the ground over radio. That stream of messages is **telemetry (TM)**.
Messages going the other way, such as "turn the heater on", are **telecommands (TC)**.

Radio links are slow (often kilobits per second), noisy and expensive, so nobody sends
JSON. Everything is tightly packed binary, defined down to the bit by standards.

### Who defines the format

| Standard | Body | What it defines | Web analogy |
|---|---|---|---|
| CCSDS 133.0-B *Space Packet Protocol* | CCSDS (NASA, ESA, JAXA …) | the **packet**: an envelope with an address and a length | an IP packet |
| ECSS-E-ST-70-41C *PUS-C* | ECSS (European) | what goes **inside**: message kinds such as "housekeeping report" | HTTP method + path |
| CCSDS 301.0-B *Time Code Formats* | CCSDS | how timestamps are encoded | `Date` header |
| CCSDS 132.0-B *TM Space Data Link* | CCSDS | **frames** that carry packets over radio (later in Phase 4) | Ethernet frame |

So one telemetry message looks like an onion:

```
┌──────────────────────────── space packet ─────────────────────────────┐
│ primary header │ PUS secondary header │  user data (parameters)  │ CRC │
│    6 bytes     │      13 bytes        │       n bytes            │  2  │
└───────────────────────────────────────────────────────────────────────┘
   "who, which,     "what kind of message,   "battery = 7.4 V, …"   "not
    how long"        when generated"                                 corrupted"
```

In round 1 we skip frames and send packets straight over TCP. That is what real missions
do inside a ground station, between the antenna equipment and the control software.

### The words you will meet

| Term | Meaning |
|---|---|
| **APID** | Application Process ID: an 11-bit "port number" saying which on-board application sent the packet (power system, payload, …) |
| **Sequence count** | 14-bit counter per APID; a gap tells the ground that a packet was lost |
| **Service / subtype** | PUS message kind. `3/25` = housekeeping parameter report: the routine "here are my sensor values" message |
| **Housekeeping (HK)** | routine health telemetry: voltages, temperatures, modes |
| **CUC** | CCSDS Unsegmented time Code: a timestamp as "seconds since an epoch", in binary |
| **CRC** | checksum; the receiver uses it to drop corrupted packets |
| **ICD** | Interface Control Document: the written contract for a binary interface. Ours comes after this tutorial |

---

## Part 1: bits and bytes in Java (read this, it saves hours)

Most bugs in codec code are not space bugs. They are Java bugs from the five traps below.

This part is a quick reference. If any of it is unclear, work through
[byte mechanics in Java](byte-mechanics.md) first: binary and hex from zero, two's
complement, every operator with worked examples, and exercises with answers.

### 1. `byte` is signed

```java
byte b = (byte) 0xC0;
System.out.println(b);          // -64, not 192
int value = b & 0xFF;           // 192: always mask when widening a byte
```

Any value of 0x80 or more in a byte array turns negative when it is read as `int`. The test
data in this tutorial deliberately contains bytes like `0xFE` and `0xFF` to catch this.

### 2. Big-endian

Network and space protocols put the most significant byte first. `ByteBuffer` is
big-endian by default, which is exactly what you want:

```java
ByteBuffer buffer = ByteBuffer.allocate(2);
buffer.putShort((short) 0x0864);   // bytes: 08 64
```

### 3. `ByteBuffer` reads are signed too

```java
int word = Short.toUnsignedInt(buffer.getShort());   // 0..65535
long seconds = Integer.toUnsignedLong(buffer.getInt()); // 0..4294967295
int service = Byte.toUnsignedInt(buffer.get());       // 0..255
```

### 4. Packing several fields into one number: shift and OR

Fields smaller than a byte share a word. To build a 16-bit word from fields of 3, 1, 1 and
11 bits, shift each field to its position and OR them together:

```
bit:   15 14 13 | 12 | 11 | 10 ........ 0
       version  |type|flag|    APID
```

```java
int word = (version << 13) | (type << 12) | (flag << 11) | apid;
```

### 5. Unpacking: shift right and mask

```java
int version = (word >>> 13) & 0b111;      // 3 bits
int type    = (word >>> 12) & 0b1;
int flag    = (word >>> 11) & 0b1;
int apid    =  word         & 0x7FF;      // 11 bits = 0b111_1111_1111
```

A mask for `n` bits is `(1 << n) - 1`. Use `>>>` (unsigned shift), not `>>`.

### Bonus: hex in tests

`java.util.HexFormat` turns hex strings into bytes, which keeps test data readable:

```java
byte[] expected = HexFormat.ofDelimiter(" ").parseHex("08 64 C0 05 00 09");
assertThat(header.encode()).isEqualTo(expected);
```

---

## Step 1: CRC-16-CCITT

**Why:** radio links flip bits. Before trusting a packet, the receiver recomputes the
checksum and compares it with the one at the end of the packet.

**The algorithm** (polynomial `0x1021`, initial value `0xFFFF`):

1. Start with `crc = 0xFFFF`.
2. For each byte: XOR the (unsigned!) byte into the **high** 8 bits of `crc`.
3. Then repeat 8 times: if the top bit (`0x8000`) is set, shift left by one and XOR with
   `0x1021`; otherwise just shift left by one. Keep only 16 bits (`& 0xFFFF`).
4. The result is `crc`.

This is "long division" of the data by the polynomial, one bit at a time. You don't need
to understand the maths to implement it correctly; the reference values below prove it.

**Tests to write first:**

| Input | Expected CRC | What it catches |
|---|---|---|
| ASCII `"123456789"` | `0x29B1` | the official check value for this CRC variant |
| empty array | `0xFFFF` | the initial value |
| ASCII `"A"` | `0xB915` | a single byte |
| any data followed by its own CRC (2 bytes, big-endian) | `0x0000` | the property receivers rely on |

**Design questions for you:** is it a static utility method or a class? Does it take
`byte[]`, or `byte[]` with offset and length (you'll want to checksum part of a buffer
in step 4)?

<details>
<summary>Hint: the core loop</summary>

```java
crc ^= (b & 0xFF) << 8;
for (int bit = 0; bit < 8; bit++) {
    crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
}
crc &= 0xFFFF;
```

Both sides of the ternary need a test to reach 100 % branch coverage; `"123456789"` already
hits both.
</details>

---

## Step 2: the space packet primary header

**Why:** this is the envelope. The ground station reads these 6 bytes first to learn who
sent the packet and how many more bytes to read.

### The layout (6 bytes, 48 bits)

```
 byte 0          byte 1          byte 2          byte 3          byte 4          byte 5
┌───┬─┬─┬───────────────────┐ ┌───┬───────────────────────────┐ ┌───────────────────────────────┐
│VVV│T│S│       APID        │ │FF │      sequence count       │ │     packet data length        │
│ 3 │1│1│        11         │ │ 2 │            14             │ │             16                │
└───┴─┴─┴───────────────────┘ └───┴───────────────────────────┘ └───────────────────────────────┘
        first 16-bit word              second word                        third word
```

| Field | Bits | Rules |
|---|---|---|
| version | 3 | always `0`. Reject anything else when decoding |
| type | 1 | `0` = telemetry, `1` = telecommand |
| secondary header flag | 1 | `1` when a PUS secondary header follows (always, for us) |
| APID | 11 | 0 to 2047. 2047 (`0x7FF`) is reserved for *idle packets* (filler) |
| sequence flags | 2 | `0b11` = unsegmented. The other values are for packets split into pieces; we never do that |
| sequence count | 14 | 0 to 16383, per APID, wraps back to 0 |
| packet data length | 16 | **number of bytes after the header, minus one** |

### Work through the example by hand first

Telemetry, secondary header present, APID 100, unsegmented, count 5, 10 bytes follow the header:

```
word 1 = (0 << 13) | (0 << 12) | (1 << 11) | 100
       = 0x0000    | 0x0000    | 0x0800    | 0x0064  = 0x0864   → bytes 08 64
word 2 = (0b11 << 14) | 5
       = 0xC000       | 0x0005                     = 0xC005   → bytes C0 05
word 3 = 10 - 1 = 9                                = 0x0009   → bytes 00 09
```

Result: **`08 64 C0 05 00 09`**. Do this on paper once; it makes everything else obvious.

### Tests to write

| Test | Data |
|---|---|
| encode the example above | → `08 64 C0 05 00 09` |
| decode it back | all fields equal the original |
| decode a header full of high bits | `17 FE FF FF FF FF` = telecommand, no secondary header, APID 2046, unsegmented, count 16383, **65536** bytes follow. Catches signed-byte bugs everywhere |
| smallest data field | 1 byte follows → length field `00 00` |
| APID 2048 or -1 | rejected |
| sequence count 16384 or -1 | rejected |
| data field of 0 or 65537 bytes | rejected |
| decoding version `1` (first byte `20`) | rejected |
| decoding fewer than 6 bytes | rejected |

### Design suggestions (your choice)

- **Store the real length, not the field.** Keep "data field length = 10" in your record and
  do the `- 1` / `+ 1` only inside encode/decode. The off-by-one then lives in exactly two
  lines, both covered by tests.
- **Value objects** make invalid headers impossible to construct, like `NoradId` in the
  backend: an `Apid` record that rejects values above 2047, an enum for the packet type.
- **A sequence counter** is a tiny class worth having: `next()` returns 0, 1, … 16383, 0, …
  The wrap is `(count + 1) & 0x3FFF`. The simulator will need one per APID.

---

## Step 3: the PUS-C telemetry secondary header

**Why:** the primary header says *who* sent the packet; the secondary header says *what
kind of message* it is and *when* it was made.

### The layout (13 bytes with our time format)

```
byte:   0          1          2          3   4          5   6          7  8  9  10   11  12
     ┌────┬────┐ ┌────────┐ ┌────────┐ ┌──────────┐ ┌──────────┐ ┌─────────────┐ ┌───────┐
     │ver │time│ │service │ │subtype │ │ message  │ │destination│ │ CUC seconds │ │  CUC  │
     │ =2 │ref │ │  type  │ │        │ │ counter  │ │    ID     │ │   coarse    │ │ fine  │
     │ 4b │ 4b │ │   8    │ │   8    │ │    16    │ │    16     │ │     32      │ │  16   │
     └────┴────┘ └────────┘ └────────┘ └──────────┘ └──────────┘ └─────────────┘ └───────┘
```

| Field | Size | Value |
|---|---|---|
| PUS version | 4 bits | `2` means PUS-C. Reject other values when decoding |
| spacecraft time reference status | 4 bits | `0` for now (it flags "on-board clock not synchronised" and similar) |
| service type | 8 bits | `3` = housekeeping |
| message subtype | 8 bits | `25` = housekeeping parameter report |
| message type counter | 16 bits | counts packets of this service/subtype, wraps at 65535 |
| destination ID | 16 bits | which ground application the packet is for, e.g. `0` |
| time | 6 bytes | CUC, see below |

So byte 0 for PUS-C with status 0 is always `0x20`.

### CUC time

CUC is "seconds since an epoch" as plain binary. Our format, which we will write in the
ICD, is:

- **coarse time:** 4 bytes of whole seconds, unsigned (so use a `long` in Java)
- **fine time:** 2 bytes of fractions of a second, in units of 1/65536 s. `0x8000` = 0.5 s

The *epoch* (what second 0 means) is a mission decision: CCSDS's default is
1958-01-01 TAI, but missions may define their own, such as 1970-01-01 UTC.

**Design tip:** keep the epoch **out of the codec**. A `CucTime(long coarse, int fine)`
record just carries the two numbers. Converting it to an `Instant` needs the epoch, which is
mission configuration, so that conversion can live in a separate small class, or even in the
backend. Then the epoch decision doesn't block you at all.

### Tests to write

| Test | Data |
|---|---|
| housekeeping header: service 3, subtype 25, counter 1, destination 0, coarse `0x12345678`, fine `0x8000` | → `20 03 19 00 01 00 00 12 34 56 78 80 00` |
| decode it back | all fields equal |
| unsigned extremes: service 255, subtype 255, counter 65535, destination 65535, coarse `0xFFFFFFFF`, fine `0xFFFF` | survives encode → decode unchanged |
| PUS version 1 when decoding (first byte `10`) | rejected |
| service 256, coarse -1 or 2^32, fine 65536 … | rejected |
| (if you write the `Instant` conversion) 0.5 s past the epoch | → fine `0x8000` |

---

## Step 4: a whole telemetry packet

Now put the pieces together:

```
primary header (6) │ secondary header (13) │ user data (n) │ CRC (2)
                   └──────────── packet data field: 13 + n + 2 bytes ─────────┘
```

- **Length:** the primary header's length field is `13 + n + 2 - 1`. Building a packet
  should compute it; the caller should never pass it in.
- **CRC:** computed over **every byte from the start of the primary header to the end of
  the user data**, and appended big-endian.
- **User data:** raw bytes for now. Which bytes mean "battery voltage" is the mission
  database, in round 2.

### The reference packet

APID 100, count 5, the secondary header from step 3, user data `01 02 03 04`:

```
08 64 C0 05 00 12                          primary header (length field 0x12 = 18 = 13 + 4 + 2 - 1)
20 03 19 00 01 00 00 12 34 56 78 80 00     secondary header
01 02 03 04                                user data
FB 2C                                      CRC
```

25 bytes in total. I computed this with an independent script, so if your encoder produces
it byte for byte, it is right.

### Tests to write

| Test | Data |
|---|---|
| build the reference packet | → exactly the 25 bytes above |
| parse the reference packet | all fields and the user data come back |
| empty user data | allowed; length field = 14 |
| one byte changed anywhere (e.g. `01` → `00`) | rejected: CRC mismatch |
| length field says more or fewer bytes than given | rejected |
| secondary header flag `0`, or type = telecommand | rejected by the TM parser |

**Watch out:** a record with a `byte[]` field gets an `equals()` that compares array
*references*, not contents. Either override `equals`/`hashCode`/`toString`, or compare
fields in tests with `isEqualTo` on the arrays. Also copy arrays coming in and going out,
or a caller can change your "immutable" packet.

---

## Step 5: reading packets from a stream

TCP gives you a stream of bytes, not messages. One `read()` might return half a packet, or
one and a half. The packet length field is how you find the boundaries:

1. Read exactly 6 bytes: the primary header.
2. Decode the data length.
3. Read exactly that many more bytes.
4. Parse the whole packet; repeat.

`InputStream.readNBytes(n)` keeps reading until it has `n` bytes or the stream ends, so it
handles fragmentation for you. It returns **fewer** bytes when the stream ends, and you
must check for that.

### The three ways a stream can end

| Situation | What should happen |
|---|---|
| ends exactly between packets (0 bytes of a new header) | normal end: no more packets (for example `Optional.empty()`) |
| ends in the middle of a header or a packet | an error: a half packet is never returned |
| delivers bytes in tiny pieces | works the same as all at once |

### Tests to write

All with in-memory streams, no network:

- two reference packets back to back in a `ByteArrayInputStream` → two packets, then the end
- an empty stream → the end straight away
- the reference packet cut after 3 bytes, and again after 10 bytes → an error each time
- a stream that returns **one byte per `read` call** → still parses. Write a small
  `InputStream` subclass in the test that wraps a `ByteArrayInputStream` and overrides
  `read(byte[], int, int)` to return at most 1 byte. Real TCP does this to you under load.

---

## When you are done

1. `./gradlew :ccsds:check` passes with the 100 % coverage gate.
2. Ask me to review. You can also ask after any single step; reviewing the CRC alone is fine.
3. Then I build the plumbing on top of your API: the simulator's TCP server sending a
   housekeeping packet every few seconds, the backend's TCP client with reconnects, the
   ICD, and the round's requirements.

## Further reading (free)

- CCSDS 133.0-B-2 *Space Packet Protocol*, section 4.1: the primary header. public.ccsds.org
- CCSDS 301.0-B-4 *Time Code Formats*, section 3.2: CUC
- ECSS-E-ST-70-41C, sections 7.4 (packet layout) and 6.3 (housekeeping service). ecss.nl, free registration
- CCSDS 130.0-G *Overview of Space Communications Protocols* (green book): the friendliest overview of the whole stack
