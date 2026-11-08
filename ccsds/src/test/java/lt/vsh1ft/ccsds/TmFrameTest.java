package lt.vsh1ft.ccsds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TmFrameTest {

    static final int DATA_FIELD_LENGTH = 56;
    private static final TmFrame EXAMPLE = new TmFrame(42, 0, 20, 5, 25, new byte[DATA_FIELD_LENGTH]);

    @Test
    void headerIsEncodedAsInTheTutorialExample() {
        final var encoded = EXAMPLE.encode();

        assertThat(encoded).hasSize(64).startsWith(Hex.bytes("02 A0 14 05 18 19"));
        assertThat(Crc16.checksum(encoded)).isZero();
    }

    @Test
    void encodedFrameDecodesToTheSameFrame() {
        final var frame = new TmFrame(1023, 7, 255, 255, TmFrame.IDLE_DATA_ONLY, new byte[] {1, 2, 3});

        assertThat(TmFrame.decode(frame.encode())).isEqualTo(frame);
        assertThat(TmFrame.decode(EXAMPLE.encode())).isEqualTo(EXAMPLE);
    }

    @Test
    void frameLengthIsHeaderDataFieldAndErrorControl() {
        assertThat(TmFrame.length(DATA_FIELD_LENGTH)).isEqualTo(64);
    }

    @Test
    void frameWithAFlippedBitFailsTheErrorControl() {
        final var encoded = EXAMPLE.encode();
        encoded[30] ^= 0x04;

        assertThatExceptionOfType(MalformedFrameException.class).isThrownBy(() -> TmFrame.decode(encoded))
                .withMessage("frame error control mismatch");
    }

    @Test
    void frameShorterThanAHeaderOneByteAndErrorControlIsRejected() {
        assertThatExceptionOfType(MalformedFrameException.class).isThrownBy(() -> TmFrame.decode(new byte[8]))
                .withMessage("a frame needs at least 9 bytes, got 8");
    }

    @ParameterizedTest
    @CsvSource({
            "42 A0 14 05 18 19 00, unsupported transfer frame version 1",
            "02 A1 14 05 18 19 00, 'expected a frame without OCF or secondary header carrying packets, got data "
                    + "field status 1100000011001'",
            "02 A0 14 05 10 19 00, 'expected a frame without OCF or secondary header carrying packets, got data "
                    + "field status 1000000011001'",
            "02 A0 14 05 98 19 00, 'expected a frame without OCF or secondary header carrying packets, got data "
                    + "field status 1001100000011001'",
            "02 A0 14 05 18 01 00, 'first header pointer must be between 0 and 0, was 1'"})
    void frameThatIsNotAPacketFrameOfThisLinkIsRejected(String header, String message) {
        final var bytes = withErrorControl(Hex.bytes(header));

        assertThatExceptionOfType(MalformedFrameException.class).isThrownBy(() -> TmFrame.decode(bytes))
                .withMessage(message);
    }

    @Test
    void everyLimitOfTheHeaderIsAccepted() {
        final var lowest = new TmFrame(0, 0, 0, 0, 0, new byte[1]);
        final var highest = new TmFrame(1023, 7, 255, 255, DATA_FIELD_LENGTH - 1, new byte[DATA_FIELD_LENGTH]);

        assertThat(TmFrame.decode(lowest.encode())).isEqualTo(lowest);
        assertThat(TmFrame.decode(highest.encode())).isEqualTo(highest);
        assertThat(new TmFrame(1, 1, 1, 1, TmFrame.NO_PACKET_START, new byte[1]).firstHeaderPointer())
                .isEqualTo(0x7FF);
    }

    @ParameterizedTest
    @CsvSource({"1024, 0, 0, 0, 0", "-1, 0, 0, 0, 0", "0, 8, 0, 0, 0", "0, 0, 256, 0, 0", "0, 0, 0, 256, 0",
            "0, 0, 0, 0, 56", "0, 0, 0, 0, -1"})
    void valuesBeyondTheHeaderFieldsAreRejected(int spacecraftId, int virtualChannel, int masterCount,
                                                int virtualCount, int firstHeaderPointer) {
        assertThatIllegalArgumentException().isThrownBy(() -> new TmFrame(spacecraftId, virtualChannel, masterCount,
                virtualCount, firstHeaderPointer, new byte[DATA_FIELD_LENGTH]));
    }

    @Test
    void dataFieldMustHoldOneTo2046Bytes() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TmFrame(0, 0, 0, 0, 0, new byte[0]));
        assertThatIllegalArgumentException().isThrownBy(() -> new TmFrame(0, 0, 0, 0, 0, new byte[2047]));
        assertThat(new TmFrame(0, 0, 0, 0, 0, new byte[2046]).encode()).hasSize(2054);
    }

    @Test
    void onlyAFrameOfIdleDataIsIdle() {
        assertThat(new TmFrame(42, 7, 0, 0, TmFrame.IDLE_DATA_ONLY, new byte[1]).idle()).isTrue();
        assertThat(EXAMPLE.idle()).isFalse();
    }

    @Test
    void dataFieldCannotBeChangedFromOutside() {
        final var data = new byte[] {1, 2};
        final var frame = new TmFrame(42, 0, 0, 0, 0, data);
        data[0] = 9;
        frame.dataField()[1] = 9;

        assertThat(frame.dataField()).containsExactly(1, 2);
    }

    @Test
    void framesWithTheSameFieldsAreEqual() {
        final var copy = new TmFrame(42, 0, 20, 5, 25, new byte[DATA_FIELD_LENGTH]);

        assertThat(copy).isEqualTo(EXAMPLE).hasSameHashCodeAs(EXAMPLE);
        assertThat(copy).isNotEqualTo(new TmFrame(43, 0, 20, 5, 25, new byte[DATA_FIELD_LENGTH]))
                .isNotEqualTo(new TmFrame(42, 1, 20, 5, 25, new byte[DATA_FIELD_LENGTH]))
                .isNotEqualTo(new TmFrame(42, 0, 21, 5, 25, new byte[DATA_FIELD_LENGTH]))
                .isNotEqualTo(new TmFrame(42, 0, 20, 6, 25, new byte[DATA_FIELD_LENGTH]))
                .isNotEqualTo(new TmFrame(42, 0, 20, 5, 26, new byte[DATA_FIELD_LENGTH]))
                .isNotEqualTo(new TmFrame(42, 0, 20, 5, 25, new byte[DATA_FIELD_LENGTH - 1]))
                .isNotEqualTo("frame");
        assertThat(new TmFrame(42, 0, 1, 2, 0, new byte[] {0x0A}).toString()).isEqualTo("TmFrame[spacecraftId=42, "
                + "virtualChannel=0, masterChannelFrameCount=1, virtualChannelFrameCount=2, firstHeaderPointer=0, "
                + "dataField=0a]");
    }

    private static byte[] withErrorControl(byte[] headerAndData) {
        final var frame = Arrays.copyOf(headerAndData, headerAndData.length + TmFrame.FECF_LENGTH);
        final var crc = Crc16.checksum(headerAndData);
        frame[frame.length - 2] = (byte) (crc >>> 8);
        frame[frame.length - 1] = (byte) crc;
        return frame;
    }
}
