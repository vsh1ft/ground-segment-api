package lt.vsh1ft.ccsds;

import static lt.vsh1ft.ccsds.MessageType.HOUSEKEEPING_PARAMETER_REPORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TmPacketTest {

    static final String REFERENCE_HEX =
            "08 64 C0 05 00 12 20 03 19 00 01 00 00 12 34 56 78 80 00 01 02 03 04 FB 2C";
    static final PusTmSecondaryHeader HOUSEKEEPING =
            new PusTmSecondaryHeader(0, HOUSEKEEPING_PARAMETER_REPORT, 1, 0, new CucTime(0x12345678L, 0x8000));
    static final TmPacket REFERENCE = new TmPacket(new Apid(100), 5, HOUSEKEEPING, Hex.bytes("01 02 03 04"));

    @Test
    void referencePacketIsEncodedByteForByte() {
        assertThat(REFERENCE.encode()).isEqualTo(Hex.bytes(REFERENCE_HEX));
    }

    @Test
    void referencePacketIsDecodedBack() {
        assertThat(TmPacket.decode(Hex.bytes(REFERENCE_HEX))).isEqualTo(REFERENCE);
    }

    @Test
    void packetWithoutUserDataHasALengthFieldOfFourteen() {
        final var packet = new TmPacket(new Apid(100), 5, HOUSEKEEPING, new byte[0]);

        final var encoded = packet.encode();

        assertThat(Arrays.copyOfRange(encoded, 4, 6)).isEqualTo(Hex.bytes("00 0E"));
        assertThat(TmPacket.decode(encoded)).isEqualTo(packet);
    }

    @Test
    void packetWithTheLargestUserDataFillsTheSixteenBitLengthField() {
        final var packet = new TmPacket(new Apid(100), 5, HOUSEKEEPING, new byte[TmPacket.MAX_USER_DATA_LENGTH]);

        final var encoded = packet.encode();

        assertThat(Arrays.copyOfRange(encoded, 4, 6)).isEqualTo(Hex.bytes("FF FF"));
        assertThat(TmPacket.decode(encoded)).isEqualTo(packet);
    }

    @Test
    void userDataLongerThanTheDataFieldAllowsIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TmPacket(new Apid(100), 5, HOUSEKEEPING, new byte[65_522]))
                .withMessage("user data length must be between 0 and 65521, was 65522");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 16384})
    void sequenceCountOutsideFourteenBitsIsRejected(int sequenceCount) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TmPacket(new Apid(100), sequenceCount, HOUSEKEEPING, new byte[0]))
                .withMessage("sequence count must be between 0 and 16383, was " + sequenceCount);
    }

    @Test
    void packetWithOneCorruptedByteIsRejected() {
        final var corrupted = Hex.bytes(REFERENCE_HEX);
        corrupted[19] = 0x00;

        assertThatExceptionOfType(MalformedPacketException.class)
                .isThrownBy(() -> TmPacket.decode(corrupted))
                .withMessage("CRC mismatch");
    }

    @Test
    void packetShorterThanItsLengthFieldIsRejected() {
        final var truncated = Arrays.copyOf(Hex.bytes(REFERENCE_HEX), 24);

        assertThatExceptionOfType(MalformedPacketException.class)
                .isThrownBy(() -> TmPacket.decode(truncated))
                .withMessage("the packet data length field says 19 bytes, but 18 follow the primary header");
    }

    @Test
    void dataFieldTooShortForASecondaryHeaderAndACrcIsRejected() {
        final var packet = Hex.bytes("08 64 C0 05 00 0D 00 00 00 00 00 00 00 00 00 00 00 00 00 00");

        assertThatExceptionOfType(MalformedPacketException.class)
                .isThrownBy(() -> TmPacket.decode(packet))
                .withMessage("a packet data field of 14 bytes is too short for a PUS TM secondary header and a CRC");
    }

    @ParameterizedTest
    @ValueSource(strings = {"18 64 C0", "00 64 C0", "08 64 40"})
    void telecommandPacketWithoutSecondaryHeaderOrSegmentIsRejected(String firstThreeBytes) {
        final var packet = Hex.bytes(firstThreeBytes + REFERENCE_HEX.substring(8));

        assertThatExceptionOfType(MalformedPacketException.class)
                .isThrownBy(() -> TmPacket.decode(packet))
                .withMessageStartingWith(
                        "expected an unsegmented telemetry packet with a secondary header, got PrimaryHeader[");
    }

    @Test
    void changingTheArraysPassedInOrHandedOutLeavesThePacketUnchanged() {
        final var userData = Hex.bytes("01 02 03 04");
        final var packet = new TmPacket(new Apid(100), 5, HOUSEKEEPING, userData);

        userData[0] = 0x7F;
        packet.userData()[1] = 0x7F;

        assertThat(packet.userData()).isEqualTo(Hex.bytes("01 02 03 04"));
    }

    @Test
    void packetsWithTheSameContentAreEqualAndHaveTheSameHashCode() {
        final var copy = new TmPacket(new Apid(100), 5, HOUSEKEEPING, Hex.bytes("01 02 03 04"));

        assertThat(copy).isEqualTo(REFERENCE).hasSameHashCodeAs(REFERENCE);
    }

    @ParameterizedTest
    @MethodSource("differentPackets")
    void packetsDifferingInAnyFieldAreNotEqual(Object other) {
        assertThat(REFERENCE).isNotEqualTo(other);
    }

    static Stream<Arguments> differentPackets() {
        final var userData = Hex.bytes("01 02 03 04");
        final var otherHeader =
                new PusTmSecondaryHeader(0, HOUSEKEEPING_PARAMETER_REPORT, 2, 0, new CucTime(0x12345678L, 0x8000));
        return Stream.of(
                Arguments.of(new TmPacket(new Apid(101), 5, HOUSEKEEPING, userData)),
                Arguments.of(new TmPacket(new Apid(100), 6, HOUSEKEEPING, userData)),
                Arguments.of(new TmPacket(new Apid(100), 5, otherHeader, userData)),
                Arguments.of(new TmPacket(new Apid(100), 5, HOUSEKEEPING, Hex.bytes("01 02 03 05"))),
                Arguments.of("not a packet"));
    }

    @Test
    void userDataIsShownInHexInToString() {
        assertThat(REFERENCE.toString())
                .startsWith("TmPacket[apid=100, sequenceCount=5, secondaryHeader=PusTmSecondaryHeader[")
                .endsWith("userData=01020304]");
    }
}
