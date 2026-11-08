package lt.vsh1ft.ccsds;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

class Crc16Test {

    @Test
    void checksumOfTheStandardCheckStringIs29B1() {
        assertThat(Crc16.checksum("123456789".getBytes(US_ASCII))).isEqualTo(0x29B1);
    }

    @Test
    void checksumOfNoBytesIsTheInitialValue() {
        assertThat(Crc16.checksum(new byte[0])).isEqualTo(0xFFFF);
    }

    @Test
    void checksumOfTheLetterAIsB915() {
        assertThat(Crc16.checksum("A".getBytes(US_ASCII))).isEqualTo(0xB915);
    }

    @Test
    void checksumOfBytesAboveSevenFIsComputedOnTheirUnsignedValue() {
        final var packet = Hex.bytes("08 64 C0 05 00 12 20 03 19 00 01 00 00 12 34 56 78 80 00 01 02 03 04");

        assertThat(Crc16.checksum(packet)).isEqualTo(0xFB2C);
    }

    @Test
    void dataFollowedByItsOwnChecksumHasAChecksumOfZero() {
        final var data = "123456789".getBytes(US_ASCII);
        final var withChecksum = ByteBuffer.allocate(data.length + 2)
                .put(data)
                .putShort((short) Crc16.checksum(data))
                .array();

        assertThat(Crc16.checksum(withChecksum)).isZero();
    }
}
