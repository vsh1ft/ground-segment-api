# Tutorial: byte mechanics in Java

Java developers rarely touch individual bits: frameworks hide them. Binary protocols such
as CCSDS are made of nothing else. This tutorial starts from zero and ends with you able to
read and write any bit-level layout in the [codec tutorial](ccsds-codec.md).

## Your playground: `jshell`

The JDK ships with a REPL. Open a terminal and type `jshell`, then try every example below
yourself. Seeing the numbers is what makes this click.

```
jshell> 0x64
$1 ==> 100

jshell> Integer.toBinaryString(100)
$2 ==> "1100100"

jshell> Integer.toHexString(-64)
$3 ==> "ffffffc0"
```

Leave with `/exit`.

---

## 1. Binary and hex

### Binary

Computers store numbers as bits, each `0` or `1`. Like decimal, but each position is worth
twice the one to its right instead of ten times:

```
position value:  128  64  32  16   8   4   2   1
bits:              0   1   1   0   0   1   0   0
                       64 +32       +4            = 100
```

8 bits make a **byte**, so one byte holds 0 to 255 (`11111111` = 255).

### Hex

Long rows of bits are unreadable, so we write them in **hexadecimal** (base 16). The trick:
**one hex digit is exactly 4 bits**. So a byte is always exactly two hex digits.

| Hex | Binary | Decimal | | Hex | Binary | Decimal |
|---|---|---|---|---|---|---|
| 0 | 0000 | 0 | | 8 | 1000 | 8 |
| 1 | 0001 | 1 | | 9 | 1001 | 9 |
| 2 | 0010 | 2 | | A | 1010 | 10 |
| 3 | 0011 | 3 | | B | 1011 | 11 |
| 4 | 0100 | 4 | | C | 1100 | 12 |
| 5 | 0101 | 5 | | D | 1101 | 13 |
| 6 | 0110 | 6 | | E | 1110 | 14 |
| 7 | 0111 | 7 | | F | 1111 | 15 |

Converting between hex and binary is just looking up each digit:

```
0x64      =  6    4     =  0110 0100
0xC005    =  C    0    0    5   =  1100 0000 0000 0101
0b1111_1110  =  1111 1110  =  F E  =  0xFE
```

You only need decimal when a human reads the value ("APID 100"). For bit work, think
in hex and binary. Learn the table above; it is the only memorising in this tutorial.

### In Java

```java
int a = 100;          // decimal
int b = 0x64;         // hex: the same number
int c = 0b0110_0100;  // binary: the same number, underscores are allowed anywhere
```

Printing:

```java
Integer.toBinaryString(100)               // "1100100"
Integer.toHexString(100)                  // "64"
String.format("%04X", 0x0864)             // "0864"  (4 digits, upper case, zero-padded)
HexFormat.of().formatHex(new byte[] {8, 100})   // "0864"
HexFormat.ofDelimiter(" ").parseHex("08 64")    // byte[] {0x08, 0x64}
```

---

## 2. Java's integer types, and why `byte` bites

| Type | Bits | Range |
|---|---|---|
| `byte` | 8 | -128 to 127 |
| `short` | 16 | -32,768 to 32,767 |
| `int` | 32 | about ±2.1 billion |
| `long` | 64 | about ±9.2 × 10¹⁸ |

Every Java integer type is **signed**. Protocols are almost always **unsigned**: a byte
there means 0 to 255. That mismatch causes most codec bugs.

### Two's complement in one paragraph

A signed type uses its top bit as the sign. For a byte: `0000 0000` to `0111 1111` are 0
to 127 as usual. `1000 0000` to `1111 1111` are the negatives, -128 to -1. The bits are
identical to the unsigned reading; only the **interpretation** differs:

| Bits | Hex | As unsigned | As Java `byte` |
|---|---|---|---|
| 0000 0101 | 05 | 5 | 5 |
| 0111 1111 | 7F | 127 | 127 |
| 1000 0000 | 80 | 128 | -128 |
| 1100 0000 | C0 | 192 | -64 |
| 1111 1110 | FE | 254 | -2 |
| 1111 1111 | FF | 255 | -1 |

Rule of thumb: unsigned value - 256 = Java byte value, when the unsigned value is 128 or more.

### Writing: casting down is safe

Casting a bigger type to a smaller one **keeps the low bits and drops the rest**. So
storing an unsigned value into a byte just works:

```java
byte b = (byte) 192;      // bits 1100 0000; prints -64, but the bits are right
byte c = (byte) 0x1234;   // keeps 0x34: the high byte is dropped
short s = (short) 0xC005; // needed: 0xC005 = 49157 is too big for a short, but the 16 bits are right
```

When you **write** bytes, the sign never matters. The bits go on the wire unchanged.

### Reading: sign extension, the classic bug

Whenever Java computes with a `byte` or `short`, it first widens it to `int`. Widening a
negative number **copies the sign bit into all the new high bits**, to keep the value -64:

```
byte  1100 0000                                  = -64
int   1111 1111 1111 1111 1111 1111 1100 0000    = -64   (0xFFFFFFC0)
```

That is correct for arithmetic and wrong for protocols, because you wanted 192. The fix is
to mask: keep only the low 8 bits (see section 3 for how `&` does that):

```java
byte b = (byte) 0xC0;
int wrong = b;            // -64        0xFFFFFFC0
int right = b & 0xFF;     // 192        0x000000C0
int alsoRight = Byte.toUnsignedInt(b);   // 192, the same thing with a readable name
```

**Every time a byte becomes an int, mask it** (or use the `toUnsigned…` helpers).

### Which type holds a field

| Unsigned field | Java type to hold it | Why |
|---|---|---|
| up to 31 bits (APID 11, count 14, length 16, service 8) | `int` | fits without touching the sign bit |
| 32 bits (CUC seconds) | `long` | 4,294,967,295 doesn't fit in an `int` |

Holding a field in `byte` or `short` just because it is 8 or 16 bits wide brings the sign
problem into your domain model. Keep fields as `int`/`long`; use `byte` only in arrays.

---

## 3. Bitwise operators

They work on each bit position independently.

### `&` AND: 1 only where both are 1

```
  1100 0101
& 0000 1111
= 0000 0101
```

Use it to **keep some bits and clear the rest** (that's "masking"):
`x & 0x0F` keeps the low 4 bits. `b & 0xFF` keeps the low 8 bits, which is why it undoes
sign extension.

Also to **test one bit**: `(x & 0x8000) != 0` is "is bit 15 set?".

### `|` OR: 1 where either is 1

```
  0000 1000 0000 0000
| 0000 0000 0110 0100
= 0000 1000 0110 0100
```

Use it to **combine fields** that don't overlap, or to **set a bit**.

### `^` XOR: 1 where they differ

```
  1100 0101
^ 0000 1111
= 1100 1010
```

Flips the bits where the mask has 1s. XOR-ing the same value twice gives the original back.
The CRC is built from XORs.

### `~` NOT: flips every bit

`~0x0F` = `0xFFFFFFF0` (remember: `int` has 32 bits). Rarely needed in our codec.

---

## 4. Shifts

### `<<` shift left

Moves every bit left, filling with 0s on the right. Each step doubles the value.

```
5        = 0000 0000 0000 0101
5 << 2   = 0000 0000 0001 0100   = 20   (5 × 2 × 2)
```

In a protocol you shift to move a field into place. The 2-bit sequence flags sit at the
top of a 16-bit word, at bits 14 and 15, so `3 << 14`:

```
3        = 0000 0000 0000 0011
3 << 14  = 1100 0000 0000 0000   = 0xC000
```

**Trap: an `int` has 32 bits, so nothing falls off at 16.**

```java
0x8000 << 1      // 0x10000, not 0x0000
(0x8000 << 1) & 0xFFFF   // 0x0000: mask to stay 16-bit
```

This is exactly why the CRC loop has `& 0xFFFF`.

### `>>>` and `>>` shift right

`>>>` moves bits right and fills with **0s** on the left. `>>` fills with **copies of the
sign bit**. They differ only for negative numbers:

```java
0xFFFFFFC0 >>> 4   // 0x0FFFFFFC
0xFFFFFFC0 >> 4    // 0xFFFFFFFC
```

For protocol work always use `>>>`. You care about bits, not signs.

### Operator precedence traps

Arithmetic binds **tighter** than shifts, and comparisons bind **tighter** than `&`:

```java
1 << n - 1        // means 1 << (n - 1), probably not what you wanted
(1 << n) - 1      // the mask for n bits

x & 0xFF == 0     // means x & (0xFF == 0): does not compile
(x & 0xFF) == 0   // what you meant
```

When in doubt, add parentheses. Nobody minds.

---

## 5. Masks

A mask for the low `n` bits is `n` ones: `(1 << n) - 1`.

| Bits | Binary | Hex | Used for |
|---|---|---|---|
| 1 | 1 | `0x1` | type, secondary header flag |
| 2 | 11 | `0x3` | sequence flags |
| 3 | 111 | `0x7` | version |
| 4 | 1111 | `0xF` | PUS version, time status |
| 8 | 1111 1111 | `0xFF` | one byte |
| 11 | 111 1111 1111 | `0x7FF` | APID |
| 14 | 11 1111 1111 1111 | `0x3FFF` | sequence count |
| 16 | 1111 1111 1111 1111 | `0xFFFF` | one 16-bit word |

Notice `0x7FF` = 2047, the largest APID, and `0x3FFF` = 16383, the largest sequence count.
A mask is also "the maximum value of the field".

---

## 6. Packing fields into a word, slowly

The first word of the primary header is 16 bits holding four fields:

```
bit:   15 14 13   12    11    10  9  8  7  6  5  4  3  2  1  0
      [version ] [type] [flag] [            APID                ]
          3        1      1                  11
```

Each field's **position** is the bit number of its lowest bit: APID starts at 0, flag at
11, type at 12, version at 13. Packing means: shift each field to its position, then OR.

Example: version 0, type 0 (TM), flag 1, APID 100.

```
version << 13 = 0 << 13 = 0000 0000 0000 0000
type    << 12 = 0 << 12 = 0000 0000 0000 0000
flag    << 11 = 1 << 11 = 0000 1000 0000 0000
apid          = 100     = 0000 0000 0110 0100
                          ───────────────────  OR
                          0000 1000 0110 0100  = 0x0864
```

```java
int word = (version << 13) | (type << 12) | (flag << 11) | apid;
```

**Why validation matters before packing.** APID 2048 is `1000 0000 0000`: 12 bits. OR-ed in,
its top bit lands on bit 11 and silently sets the secondary header flag. No exception,
just a wrong packet. That's why `Apid` must reject values above `0x7FF`. You could also
write `apid & 0x7FF` to cut it down, but that hides the bug instead of reporting it.

---

## 7. Unpacking a word, slowly

The reverse: shift the field down to position 0, then mask away everything above it.

Example: word `0x17FE` = `0001 0111 1111 1110`.

```
version: 0x17FE >>> 13 = 0000 0000 0000 0000   & 0x7   = 0   ✓ version 0
type:    0x17FE >>> 12 = 0000 0000 0000 0001   & 0x1   = 1   → telecommand
flag:    0x17FE >>> 11 = 0000 0000 0000 0010   & 0x1   = 0   → no secondary header
apid:    0x17FE        = 0001 0111 1111 1110   & 0x7FF = 111 1111 1110 = 0x7FE = 2046
```

```java
int version = (word >>> 13) & 0x7;
int type    = (word >>> 12) & 0x1;
int flag    = (word >>> 11) & 0x1;
int apid    =  word         & 0x7FF;
```

Look at `flag`: after shifting by 11 the value is `10` in binary. Without `& 0x1` you would
read 2, because the type bit is still there. The mask is not optional.

---

## 8. Numbers bigger than a byte: endianness

A 16-bit value needs two bytes. **Big-endian** (the network and space standard) sends the
high byte first: `0x0864` goes on the wire as `08`, then `64`. A 32-bit value
`0x12345678` goes as `12 34 56 78`.

### By hand: writing

```java
bytes[0] = (byte) (word >>> 8);   // high byte: shift it down, the cast keeps 8 bits
bytes[1] = (byte) word;           // low byte: the cast drops everything above 8 bits
```

### By hand: reading, and the bug that destroys the *other* byte

```java
int word = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
```

Forget the mask on the **low** byte and watch what happens with the bytes `08 FE`:

```
(bytes[0] & 0xFF) << 8 = 0000 0000 0000 0000 0000 1000 0000 0000   0x00000800
bytes[1]  (= -2)      = 1111 1111 1111 1111 1111 1111 1111 1110   0xFFFFFFFE  sign-extended!
                        ───────────────────────────────────────  OR
                        1111 1111 1111 1111 1111 1111 1111 1110   = -2
```

The sign-extension 1s overwrite the high byte: the `08` is gone and you get -2 instead of
`0x08FE` = 2302. And the bug only shows up when the low byte is 0x80 or more, so tests with
small numbers pass. That's why the codec tutorial's test data is full of `FE` and `FF`.

---

## 9. `ByteBuffer`: let the JDK do the byte shuffling

`java.nio.ByteBuffer` reads and writes multi-byte values for you, big-endian by default.

### Writing

```java
byte[] header = ByteBuffer.allocate(6)
        .putShort((short) 0x0864)
        .putShort((short) 0xC005)
        .putShort((short) 0x0009)
        .array();                      // 08 64 C0 05 00 09
```

The `(short)` casts are needed because Java won't silently narrow an `int`. As section 2
says, the cast keeps the right bits even for values above 32767.

### Reading

```java
ByteBuffer buffer = ByteBuffer.wrap(header);
int word1 = Short.toUnsignedInt(buffer.getShort());   // 0x0864
int word2 = Short.toUnsignedInt(buffer.getShort());   // 0xC005
int word3 = Short.toUnsignedInt(buffer.getShort());   // 9
```

`getShort()` returns a signed `short`, so wrap it in `Short.toUnsignedInt`, otherwise
`0xC005` comes back as -16379. The same for the others:

| Read | Returns | Make it unsigned with |
|---|---|---|
| `get()` | `byte` | `Byte.toUnsignedInt(…)` |
| `getShort()` | `short` | `Short.toUnsignedInt(…)` |
| `getInt()` | `int` | `Integer.toUnsignedLong(…)` |

### The cursor

A buffer has a **position** that each relative `get`/`put` advances. That is handy: decode
the primary header, and the buffer is already positioned at the secondary header.

```java
buffer.position()      // where the next read happens
buffer.remaining()     // bytes left to read
buffer.get(bytes)      // fills the array, advances by bytes.length
buffer.getShort(4)     // absolute read at index 4, does not move the cursor
```

Reading past the end throws `BufferUnderflowException`. Decide whether your decoder lets
that escape or checks `remaining()` first and throws a clearer exception of its own.

---

## 10. Exercises

Do them in `jshell` or on paper. Answers are folded.

1. What is `0x7FF` in decimal, and why does that number matter in CCSDS?
2. What does `jshell` print for `(byte) 0xFE`? And for `(byte) 0xFE & 0xFF`?
3. Pack the first header word for: telecommand, secondary header present, APID 2046.
4. The second header word is `0xC005`. Extract the sequence flags and the sequence count.
5. Which 4 bytes does `ByteBuffer.allocate(4).putInt(0x12345678)` contain, in order?
6. What is `0xFFFF + 1` in Java, and what is `(0xFFFF + 1) & 0xFFFF`?
7. A sequence counter is at 16383. Show that `(count + 1) & 0x3FFF` wraps it to 0.
8. CUC fine time counts in units of 1/65536 s. What are 0.5 s and 0.25 s in hex?

<details>
<summary>Answers</summary>

1. 2047. It is the largest 11-bit value, so the largest APID, and 2047 is reserved for idle packets.
2. `-2`, then `254`.
3. `(0 << 13) | (1 << 12) | (1 << 11) | 0x7FE` = `0x1000 | 0x0800 | 0x07FE` = `0x1FFE`, bytes `1F FE`.
4. Flags: `0xC005 >>> 14` = `0b11` = 3 (unsegmented). Count: `0xC005 & 0x3FFF` = 5.
5. `12 34 56 78` (big-endian: most significant first).
6. `0x10000` = 65536, because an `int` has room for bit 16. Masked: `0`. This is how a 16-bit counter wraps.
7. 16383 = `0x3FFF`; + 1 = `0x4000` = `0100 0000 0000 0000`; `& 0x3FFF` (`0011 1111 1111 1111`) = 0.
8. 0.5 × 65536 = 32768 = `0x8000`; 0.25 × 65536 = 16384 = `0x4000`.
</details>

When these feel easy, go back to [the codec tutorial](ccsds-codec.md), Step 1.
