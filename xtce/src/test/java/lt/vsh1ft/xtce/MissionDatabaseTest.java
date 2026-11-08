package lt.vsh1ft.xtce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class MissionDatabaseTest {

    private static final String HEADERS = "0864C0210014 200319002100008182973AEC54";
    private static final String EVENT_HEADERS = "0864C32C0012 2005";
    private static final MissionDatabase HOUSEKEEPING = Xtce.housekeeping();

    @Test
    void simulatorPacketIsDecodedDownToHousekeepingStructure1() {
        final var decoded = HOUSEKEEPING.decode(Xtce.bytes(HEADERS + "01 1CF3 009E 01 ACC1"));

        assertThat(decoded.container().name()).isEqualTo("HK_STRUCTURE_1");
        assertThat(decoded.complete()).isTrue();
        assertThat(value(decoded, "BATTERY_VOLTAGE").raw()).isEqualTo(7411);
        assertThat(engineering(decoded, "BATTERY_VOLTAGE")).isInstanceOfSatisfying(FloatValue.class,
                voltage -> assertThat(voltage.value()).isCloseTo(7.411, within(1e-9)));
        assertThat(engineering(decoded, "PLATFORM_TEMPERATURE")).isInstanceOfSatisfying(FloatValue.class,
                temperature -> assertThat(temperature.value()).isCloseTo(15.8, within(1e-9)));
        assertThat(engineering(decoded, "SPACECRAFT_MODE")).isEqualTo(new EnumeratedValue("NOMINAL"));
    }

    @Test
    void headerFieldsAreDecodedAsParametersInPacketOrder() {
        final var decoded = HOUSEKEEPING.decode(Xtce.bytes(HEADERS + "01 1CF3 009E 01 ACC1"));

        assertThat(decoded.values()).extracting(value -> value.parameter().name()).startsWith("PACKET_VERSION",
                "PACKET_TYPE", "SECONDARY_HEADER_FLAG", "APID", "SEQUENCE_FLAGS", "SEQUENCE_COUNT").hasSize(19);
        assertThat(engineering(decoded, "APID")).isEqualTo(new IntegerValue(100));
        assertThat(engineering(decoded, "SEQUENCE_COUNT")).isEqualTo(new IntegerValue(33));
        assertThat(engineering(decoded, "TIME_COARSE")).isEqualTo(new IntegerValue(2_172_819_258L));
        assertThat(engineering(decoded, "TIME_FINE")).isEqualTo(new IntegerValue(60_500));
    }

    @Test
    void negativeTemperatureIsReadAsTwosComplement() {
        final var decoded = HOUSEKEEPING.decode(Xtce.bytes(HEADERS + "01 1D4C FFCE 00"));

        assertThat(value(decoded, "PLATFORM_TEMPERATURE").raw()).isEqualTo(-50);
        assertThat(engineering(decoded, "PLATFORM_TEMPERATURE")).isInstanceOfSatisfying(FloatValue.class,
                temperature -> assertThat(temperature.value()).isCloseTo(-5.0, within(1e-9)));
        assertThat(engineering(decoded, "SPACECRAFT_MODE")).isEqualTo(new EnumeratedValue("SAFE"));
    }

    @Test
    void unknownHousekeepingStructureEndsInTheAbstractReport() {
        final var decoded = HOUSEKEEPING.decode(Xtce.bytes(HEADERS + "02 1CF3 009E 01"));

        assertThat(decoded.container().name()).isEqualTo("PUS_HK_REPORT");
        assertThat(decoded.complete()).isFalse();
        assertThat(decoded.values()).hasSize(16);
    }

    @Test
    void modeWithoutALabelKeepsItsRawValueButHasNoEngineeringValue() {
        final var decoded = HOUSEKEEPING.decode(Xtce.bytes(HEADERS + "01 1CF3 009E 07"));

        assertThat(value(decoded, "SPACECRAFT_MODE").raw()).isEqualTo(7);
        assertThat(value(decoded, "SPACECRAFT_MODE").engineering()).isEmpty();
    }

    @Test
    void packetShorterThanItsLayoutCannotBeDecoded() {
        assertThatThrownBy(() -> HOUSEKEEPING.decode(Xtce.bytes(HEADERS + "01 1CF3")))
                .isInstanceOf(PacketDecodingException.class)
                .hasMessage("Packet has 176 bits, needed 16 more from bit 176");
    }

    @Test
    void packetMatchingTwoContainersIsAmbiguous() {
        final var id = new Parameter("ID", null, new IntegerParameterType("uint8_t", null,
                new IntegerEncoding(8, false), AlarmRanges.NONE));
        final var one = new Comparison(id, new IntegerValue(1), true);
        final var root = new SequenceContainer("ROOT", true, List.of(new SequenceContainer.Entry(id)), List.of(
                new SequenceContainer.Child(new SequenceContainer("A", false, List.of(), List.of()), List.of(one)),
                new SequenceContainer.Child(new SequenceContainer("B", false, List.of(), List.of()), List.of(one))));

        assertThatThrownBy(() -> new MissionDatabase("TEST", List.of(id), root).decode(new byte[] {1}))
                .isInstanceOf(PacketDecodingException.class)
                .hasMessage("Packet matches several containers based on ROOT: [A, B]");
    }

    @Test
    void comparisonOnTheRawValueIgnoresTheCalibration() {
        final var mode = new Parameter("MODE", null, new EnumeratedParameterType("mode_t",
                new IntegerEncoding(8, false), Map.of(1L, "NOMINAL"), Map.of()));
        final var raw = new Comparison(mode, new IntegerValue(1), false);
        final var root = new SequenceContainer("ROOT", true, List.of(new SequenceContainer.Entry(mode)), List.of(
                new SequenceContainer.Child(new SequenceContainer("NOMINAL_ONLY", false, List.of(), List.of()),
                        List.of(raw))));

        assertThat(new MissionDatabase("TEST", List.of(mode), root).decode(new byte[] {1}).container().name())
                .isEqualTo("NOMINAL_ONLY");
    }

    @Test
    void eventReportIsDecodedByComparingTheEventIdLabel() {
        final var decoded = HOUSEKEEPING.decode(Xtce.bytes(EVENT_HEADERS + "02 00000000 818298B50000 0001 1BED 009A"));

        assertThat(decoded.container().name()).isEqualTo("EVENT_BATTERY_LOW");
        assertThat(engineering(decoded, "EVENT_ID")).isEqualTo(new EnumeratedValue("BATTERY_LOW"));
        assertThat(engineering(decoded, "EVENT_BATTERY_VOLTAGE")).isInstanceOfSatisfying(FloatValue.class,
                voltage -> assertThat(voltage.value()).isCloseTo(7.149, within(1e-9)));
    }

    @Test
    void eventWithoutAuxiliaryDataEndsInItsOwnEmptyContainer() {
        final var decoded = HOUSEKEEPING.decode(Xtce.bytes("0864C32D0010 2005 0400000000 818298B50000 0005 B226"));

        assertThat(decoded.container().name()).isEqualTo("EVENT_SAFE_MODE_ENTERED");
        assertThat(decoded.complete()).isTrue();
        assertThat(decoded.container().entries()).isEmpty();
    }

    @Test
    void unknownEventIdEndsInTheAbstractEventReportWithItsRawId() {
        final var decoded = HOUSEKEEPING.decode(Xtce.bytes(EVENT_HEADERS + "04 00000000 818298B50000 0063"));

        assertThat(decoded.container().name()).isEqualTo("PUS_EVENT");
        assertThat(decoded.complete()).isFalse();
        assertThat(value(decoded, "EVENT_ID").raw()).isEqualTo(99);
        assertThat(value(decoded, "EVENT_ID").engineering()).isEmpty();
    }

    @Test
    void enumerationLabelsCarryTheirDescriptions() {
        final var eventId = (EnumeratedParameterType) HOUSEKEEPING.parameter("EVENT_ID").orElseThrow().type();

        assertThat(eventId.description(1)).hasValue("Battery voltage below 7.15 V");
        assertThat(eventId.description(99)).isEmpty();
    }

    @Test
    void containersAreFoundByNameAnywhereInTheTree() {
        assertThat(HOUSEKEEPING.container("CCSDS_PACKET")).hasValue(HOUSEKEEPING.root());
        assertThat(HOUSEKEEPING.container("EVENT_TEMPERATURE_HIGH")).hasValueSatisfying(container ->
                assertThat(container.parameters()).extracting(Parameter::name).containsExactly("EVENT_TEMPERATURE"));
        assertThat(HOUSEKEEPING.container("EVENT_SOLAR_FLARE")).isEmpty();
    }

    @Test
    void parametersAreFoundByName() {
        assertThat(HOUSEKEEPING.parameter("BATTERY_VOLTAGE")).hasValueSatisfying(battery -> {
            assertThat(battery.description()).isEqualTo("Battery voltage");
            assertThat(battery.type().unit()).isEqualTo("V");
        });
        assertThat(HOUSEKEEPING.parameter("SOLAR_ARRAY_CURRENT")).isEmpty();
    }

    private static ParameterValue value(DecodedPacket decoded, String name) {
        return decoded.values().stream().filter(value -> value.parameter().name().equals(name)).findFirst()
                .orElseThrow();
    }

    private static EngineeringValue engineering(DecodedPacket decoded, String name) {
        final Optional<EngineeringValue> engineering = value(decoded, name).engineering();
        return engineering.orElseThrow();
    }

    @Test
    void littleEndianIntegersAreReadLeastSignificantByteFirst() {
        final var database = single(new IntegerParameterType("t", null, new IntegerEncoding(32, true, true),
                AlarmRanges.NONE), 0);

        assertThat(database.decode(Xtce.bytes("C2 33 07 19")).values().getFirst().raw()).isEqualTo(0x190733C2L);
        assertThat(database.decode(Xtce.bytes("FE FF FF FF")).values().getFirst().raw()).isEqualTo(-2);
    }

    @Test
    void sixtyFourBitIntegersKeepEveryBit() {
        final var unsigned = single(new IntegerParameterType("t", null, new IntegerEncoding(64, false, true),
                AlarmRanges.NONE), 0);
        final var signed = single(new IntegerParameterType("t", null, new IntegerEncoding(64, true),
                AlarmRanges.NONE), 0);

        assertThat(unsigned.decode(Xtce.bytes("B2 98 6D BB A1 01 00 00")).values().getFirst().raw())
                .isEqualTo(1_794_145_884_338L);
        assertThat(signed.decode(Xtce.bytes("FF FF FF FF FF FF FF FE")).values().getFirst().raw()).isEqualTo(-2);
    }

    @Test
    void fixedSizeStringEndsAtItsFirstZeroByte() {
        final var database = single(new StringParameterType("s", new IntegerEncoding(32, false)), 0);

        assertThat(database.decode(Xtce.bytes("43 54 53 31")).values().getFirst().engineering())
                .contains(new StringValue("CTS1"));
        assertThat(database.decode(Xtce.bytes("20 53 35 00")).values().getFirst().engineering())
                .contains(new StringValue(" S5"));
        assertThat(database.decode(Xtce.bytes("00 41 42 43")).values().getFirst().engineering())
                .contains(new StringValue(""));
    }

    @Test
    void absoluteTimeIsTheEpochPlusTheScaledCountAndGivesTheGenerationTime() {
        final var database = single(new AbsoluteTimeParameterType("t", new IntegerEncoding(64, false, true),
                Instant.EPOCH, new BigDecimal("0.001"), BigDecimal.ZERO), 0);

        final var decoded = database.decode(Xtce.bytes("B2 98 6D BB A1 01 00 00"));

        assertThat(decoded.values().getFirst().engineering())
                .contains(new TimeValue(Instant.parse("2026-11-08T13:51:24.338Z")));
        assertThat(decoded.generationTime()).hasValue(Instant.parse("2026-11-08T13:51:24.338Z"));
    }

    @Test
    void timeOffsetIsAddedAfterScaling() {
        final var tai = Instant.parse("1958-01-01T00:00:00Z");
        final var database = single(new AbsoluteTimeParameterType("t", new IntegerEncoding(8, false), tai,
                new BigDecimal("2"), new BigDecimal("0.5")), 0);

        assertThat(database.decode(new byte[] {3}).generationTime()).hasValue(tai.plusMillis(6500));
    }

    @Test
    void packetWithoutAnAbsoluteTimeHasNoGenerationTime() {
        assertThat(HOUSEKEEPING.decode(Xtce.bytes(HEADERS + "01 1CF3 009E 01 ACC1")).generationTime()).isEmpty();
    }

    @Test
    void entryOffsetSkipsBitsBeforeTheEntry() {
        final var database = single(new IntegerParameterType("t", null, new IntegerEncoding(8, false),
                AlarmRanges.NONE), 16);

        assertThat(database.decode(Xtce.bytes("AA BB 07")).values().getFirst().raw()).isEqualTo(7);
        assertThatThrownBy(() -> database.decode(Xtce.bytes("AA"))).isInstanceOf(PacketDecodingException.class)
                .hasMessage("Packet has 8 bits, needed 16 more from bit 0");
    }

    private static MissionDatabase single(ParameterType type, long offsetInBits) {
        final var parameter = new Parameter("P", null, type);
        return new MissionDatabase("TEST", List.of(parameter), new SequenceContainer("ROOT", false,
                List.of(new SequenceContainer.Entry(parameter, offsetInBits)), List.of()));
    }
}
