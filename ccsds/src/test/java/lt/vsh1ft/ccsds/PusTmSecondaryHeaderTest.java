package lt.vsh1ft.ccsds;

import static lt.vsh1ft.ccsds.MessageType.HOUSEKEEPING_PARAMETER_REPORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PusTmSecondaryHeaderTest {

    private static final CucTime TIME = new CucTime(0x12345678L, 0x8000);
    private static final PusTmSecondaryHeader HOUSEKEEPING =
            new PusTmSecondaryHeader(0, HOUSEKEEPING_PARAMETER_REPORT, 1, 0, TIME);
    private static final PusTmSecondaryHeader ALL_BITS_HIGH = new PusTmSecondaryHeader(
            15, new MessageType(255, 255), 65_535, 65_535, new CucTime(4_294_967_295L, 65_535));

    @Test
    void housekeepingReportHeaderIsWrittenAsPusCService3Subtype25() {
        assertThat(encode(HOUSEKEEPING)).isEqualTo(Hex.bytes("20 03 19 00 01 00 00 12 34 56 78 80 00"));
    }

    @Test
    void housekeepingReportHeaderIsReadBack() {
        assertThat(decode("20 03 19 00 01 00 00 12 34 56 78 80 00")).isEqualTo(HOUSEKEEPING);
    }

    @Test
    void headerWithEveryFieldAtItsMaximumIsWrittenWithAllBitsHighExceptThePusVersion() {
        assertThat(encode(ALL_BITS_HIGH)).isEqualTo(Hex.bytes("2F FF FF FF FF FF FF FF FF FF FF FF FF"));
    }

    @Test
    void headerWithBytesAboveSevenFIsReadAsUnsignedValues() {
        assertThat(decode("2F FF FF FF FF FF FF FF FF FF FF FF FF")).isEqualTo(ALL_BITS_HIGH);
    }

    @Test
    void headerWithPusVersionOneIsRejected() {
        assertThatExceptionOfType(MalformedPacketException.class)
                .isThrownBy(() -> decode("10 03 19 00 01 00 00 12 34 56 78 80 00"))
                .withMessage("unsupported PUS version 1");
    }

    @Test
    void twelveBytesAreTooFewForAHeader() {
        assertThatExceptionOfType(MalformedPacketException.class)
                .isThrownBy(() -> decode("20 03 19 00 01 00 00 12 34 56 78 80"))
                .withMessage("a PUS TM secondary header needs 13 bytes, only 12 left");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 16})
    void timeReferenceStatusOutsideFourBitsIsRejected(int status) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PusTmSecondaryHeader(status, HOUSEKEEPING_PARAMETER_REPORT, 1, 0, TIME))
                .withMessage("spacecraft time reference status must be between 0 and 15, was " + status);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 65_536})
    void messageTypeCounterOutsideSixteenBitsIsRejected(int counter) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PusTmSecondaryHeader(0, HOUSEKEEPING_PARAMETER_REPORT, counter, 0, TIME))
                .withMessage("message type counter must be between 0 and 65535, was " + counter);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 65_536})
    void destinationIdOutsideSixteenBitsIsRejected(int destinationId) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PusTmSecondaryHeader(0, HOUSEKEEPING_PARAMETER_REPORT, 1, destinationId, TIME))
                .withMessage("destination ID must be between 0 and 65535, was " + destinationId);
    }

    private static byte[] encode(PusTmSecondaryHeader header) {
        final var buffer = ByteBuffer.allocate(PusTmSecondaryHeader.LENGTH);
        header.writeTo(buffer);
        return buffer.array();
    }

    private static PusTmSecondaryHeader decode(String hex) {
        return PusTmSecondaryHeader.readFrom(ByteBuffer.wrap(Hex.bytes(hex)));
    }
}
