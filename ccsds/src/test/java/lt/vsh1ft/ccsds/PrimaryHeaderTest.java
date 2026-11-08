package lt.vsh1ft.ccsds;

import static lt.vsh1ft.ccsds.PacketType.TELECOMMAND;
import static lt.vsh1ft.ccsds.PacketType.TELEMETRY;
import static lt.vsh1ft.ccsds.SequenceFlags.UNSEGMENTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PrimaryHeaderTest {

    private static final PrimaryHeader HOUSEKEEPING = new PrimaryHeader(TELEMETRY, true, new Apid(100), UNSEGMENTED, 5, 10);
    private static final PrimaryHeader ALL_BITS_HIGH =
            new PrimaryHeader(TELECOMMAND, false, new Apid(2046), UNSEGMENTED, 16383, 65536);

    @Test
    void headerOfTheWorkedExampleIsWrittenAs0864C0050009() {
        assertThat(encode(HOUSEKEEPING)).isEqualTo(Hex.bytes("08 64 C0 05 00 09"));
    }

    @Test
    void headerOfTheWorkedExampleIsReadBack() {
        assertThat(decode("08 64 C0 05 00 09")).isEqualTo(HOUSEKEEPING);
    }

    @Test
    void headerWithEveryFieldNearItsMaximumIsWrittenAs17FEFFFFFFFF() {
        assertThat(encode(ALL_BITS_HIGH)).isEqualTo(Hex.bytes("17 FE FF FF FF FF"));
    }

    @Test
    void headerWithBytesAboveSevenFIsReadAsUnsignedValues() {
        assertThat(decode("17 FE FF FF FF FF")).isEqualTo(ALL_BITS_HIGH);
    }

    @Test
    void dataFieldOfOneByteHasALengthFieldOfZero() {
        final var header = new PrimaryHeader(TELEMETRY, true, new Apid(100), UNSEGMENTED, 5, 1);

        assertThat(encode(header)).isEqualTo(Hex.bytes("08 64 C0 05 00 00"));
    }

    @ParameterizedTest
    @CsvSource({
            "00, CONTINUATION_SEGMENT",
            "40, FIRST_SEGMENT",
            "80, LAST_SEGMENT",
            "C0, UNSEGMENTED"
    })
    void sequenceFlagsAreReadFromTheTopTwoBitsOfTheThirdByte(String thirdByte, SequenceFlags expected) {
        assertThat(decode("08 64 " + thirdByte + " 05 00 09").sequenceFlags()).isEqualTo(expected);
    }

    @Test
    void readingLeavesTheBufferAtTheFirstByteAfterTheHeader() {
        final var buffer = ByteBuffer.wrap(Hex.bytes("08 64 C0 05 00 09 20"));

        PrimaryHeader.readFrom(buffer);

        assertThat(buffer.position()).isEqualTo(PrimaryHeader.LENGTH);
    }

    @Test
    void headerWithVersionOneIsRejected() {
        assertThatExceptionOfType(MalformedPacketException.class)
                .isThrownBy(() -> decode("28 64 C0 05 00 09"))
                .withMessage("unsupported packet version 1");
    }

    @Test
    void fiveBytesAreTooFewForAHeader() {
        assertThatExceptionOfType(MalformedPacketException.class)
                .isThrownBy(() -> decode("08 64 C0 05 00"))
                .withMessage("a primary header needs 6 bytes, only 5 left");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 16384})
    void sequenceCountOutsideFourteenBitsIsRejected(int sequenceCount) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PrimaryHeader(TELEMETRY, true, new Apid(100), UNSEGMENTED, sequenceCount, 10))
                .withMessage("sequence count must be between 0 and 16383, was " + sequenceCount);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 65537})
    void dataFieldLengthOutsideOneTo65536IsRejected(int dataFieldLength) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PrimaryHeader(TELEMETRY, true, new Apid(100), UNSEGMENTED, 5, dataFieldLength))
                .withMessage("packet data field length must be between 1 and 65536, was " + dataFieldLength);
    }

    private static byte[] encode(PrimaryHeader header) {
        final var buffer = ByteBuffer.allocate(PrimaryHeader.LENGTH);
        header.writeTo(buffer);
        return buffer.array();
    }

    private static PrimaryHeader decode(String hex) {
        return PrimaryHeader.readFrom(ByteBuffer.wrap(Hex.bytes(hex)));
    }
}
