package lt.vsh1ft.ccsds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TmPacketReaderTest {

    private static final byte[] REFERENCE = Hex.bytes(TmPacketTest.REFERENCE_HEX);

    @Test
    void twoPacketsBackToBackAreReadOneAfterTheOther() throws IOException {
        final var reader = new TmPacketReader(new ByteArrayInputStream(concat(REFERENCE, REFERENCE)));

        assertThat(reader.next()).contains(TmPacketTest.REFERENCE);
        assertThat(reader.next()).contains(TmPacketTest.REFERENCE);
        assertThat(reader.next()).isEmpty();
    }

    @Test
    void emptyStreamHasNoPackets() throws IOException {
        final var reader = new TmPacketReader(new ByteArrayInputStream(new byte[0]));

        assertThat(reader.next()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "3, 'expected 6 bytes, got 3'",
            "10, 'expected 19 bytes, got 4'"
    })
    void streamEndingInsideAPacketIsAnError(int length, String detail) {
        final var reader = new TmPacketReader(new ByteArrayInputStream(Arrays.copyOf(REFERENCE, length)));

        assertThatExceptionOfType(EOFException.class)
                .isThrownBy(reader::next)
                .withMessage("the stream ended inside a packet: " + detail);
    }

    @Test
    void streamDeliveringOneByteAtATimeIsReadLikeAnyOther() throws IOException {
        final var reader = new TmPacketReader(new OneByteAtATime(REFERENCE));

        assertThat(reader.next()).contains(TmPacketTest.REFERENCE);
    }

    @Test
    void corruptedPacketDoesNotDisturbThePacketAfterIt() throws IOException {
        final var corrupted = REFERENCE.clone();
        corrupted[19] = 0x00;
        final var reader = new TmPacketReader(new ByteArrayInputStream(concat(corrupted, REFERENCE)));

        assertThatExceptionOfType(MalformedPacketException.class).isThrownBy(reader::next);
        assertThat(reader.next()).contains(TmPacketTest.REFERENCE);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        return ByteBuffer.allocate(first.length + second.length).put(first).put(second).array();
    }

    private static final class OneByteAtATime extends InputStream {

        private final ByteArrayInputStream bytes;

        OneByteAtATime(byte[] bytes) {
            this.bytes = new ByteArrayInputStream(bytes);
        }

        @Override
        public int read() {
            return bytes.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return bytes.read(buffer, offset, Math.min(length, 1));
        }
    }
}
