package lt.vsh1ft.xtce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class MissionDatabaseTest {

    private static final String HEADERS = "0864C0210014 200319002100008182973AEC54";
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
                new IntegerEncoding(8, false)));
        final var one = new Comparison(id, new IntegerValue(1), true);
        final var root = new SequenceContainer("ROOT", true, List.of(id), List.of(
                new SequenceContainer.Child(new SequenceContainer("A", false, List.of(), List.of()), List.of(one)),
                new SequenceContainer.Child(new SequenceContainer("B", false, List.of(), List.of()), List.of(one))));

        assertThatThrownBy(() -> new MissionDatabase("TEST", List.of(id), root).decode(new byte[] {1}))
                .isInstanceOf(PacketDecodingException.class)
                .hasMessage("Packet matches several containers based on ROOT: [A, B]");
    }

    @Test
    void comparisonOnTheRawValueIgnoresTheCalibration() {
        final var mode = new Parameter("MODE", null, new EnumeratedParameterType("mode_t",
                new IntegerEncoding(8, false), Map.of(1L, "NOMINAL")));
        final var raw = new Comparison(mode, new IntegerValue(1), false);
        final var root = new SequenceContainer("ROOT", true, List.of(mode), List.of(
                new SequenceContainer.Child(new SequenceContainer("NOMINAL_ONLY", false, List.of(), List.of()),
                        List.of(raw))));

        assertThat(new MissionDatabase("TEST", List.of(mode), root).decode(new byte[] {1}).container().name())
                .isEqualTo("NOMINAL_ONLY");
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
}
