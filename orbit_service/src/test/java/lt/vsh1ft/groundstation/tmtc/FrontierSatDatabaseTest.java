package lt.vsh1ft.groundstation.tmtc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import lt.vsh1ft.groundstation.traceability.Requirement;
import lt.vsh1ft.xtce.DecodedPacket;
import lt.vsh1ft.xtce.EngineeringValue;
import lt.vsh1ft.xtce.EnumeratedValue;
import lt.vsh1ft.xtce.FloatValue;
import lt.vsh1ft.xtce.ParameterValue;
import lt.vsh1ft.xtce.StringValue;
import lt.vsh1ft.xtce.TimeValue;
import org.junit.jupiter.api.Test;

class FrontierSatDatabaseTest {

    private static final MissionDatabases DATABASES = FrontierSat.databases();
    private static final DecodedPacket BEACON = DATABASES.of(FrontierSat.SATELLITE).orElseThrow()
            .decode(FrameCheck.CRC32C_TRAILER.check(FrontierSat.beacon()).content());

    @Test
    @Requirement({"SAT-030", "PAR-060"})
    void everyFieldOfARealBeaconMatchesTheFirmwareStructDecodedIndependently() {
        final var decoded = BEACON.values().stream()
                .collect(Collectors.toMap(value -> value.parameter().name(), ParameterValue::raw));

        assertThat(BEACON.container().name()).isEqualTo("BEACON_EXTENDED_V5");
        assertThat(BEACON.complete()).isTrue();
        assertThat(decoded).containsExactlyInAnyOrderEntriesOf(FrontierSat.expectedRawValues());
    }

    @Test
    @Requirement({"SAT-030", "PAR-060"})
    void beaconValuesAreCalibratedToEngineeringUnits() {
        assertThat(engineering("SATELLITE_NAME")).contains(new StringValue("CTS1"));
        assertThat(engineering("END_MESSAGE")).contains(new StringValue(" S5"));
        assertThat(engineering("CTS1_OPERATION_STATE")).contains(new EnumeratedValue("NOMINAL_WITH_RADIO_TX"));
        assertThat(engineering("PACKET_TYPE")).contains(new EnumeratedValue("BEACON_EXTENDED_V5PLUS"));
        assertThat(number("OBC_TEMPERATURE")).isCloseTo(29.25, within(1e-9));
        assertThat(number("OBC_ADC_BATTERY_VOLTAGE")).isCloseTo(14.622, within(1e-9));
        assertThat(number("EPS_BATTERY_TEMPERATURE_0")).isCloseTo(16.04, within(1e-9));
        assertThat(engineering("UNIX_EPOCH_TIME"))
                .contains(new TimeValue(Instant.parse("2026-11-08T13:51:24.338Z")));
    }

    @Test
    @Requirement("SAT-020")
    void beaconIsDatedByTheOnBoardClock() {
        assertThat(BEACON.generationTime()).hasValue(Instant.parse("2026-11-08T13:51:24.338Z"));
    }

    @Test
    @Requirement("SAT-030")
    void otherPacketTypesEndInTheAbstractFrameContainer() {
        final var gnss = Arrays.copyOf(FrontierSat.beacon(), 40);
        gnss[4] = 0x30;

        final var decoded = DATABASES.of(FrontierSat.SATELLITE).orElseThrow().decode(gnss);

        assertThat(decoded.container().name()).isEqualTo("FRONTIERSAT_FRAME");
        assertThat(decoded.complete()).isFalse();
    }

    @Test
    @Requirement("SAT-020")
    void validCrc32cTrailerIsCheckedAndRemoved() {
        final var checked = FrameCheck.CRC32C_TRAILER.check(FrontierSat.beacon());

        assertThat(checked.crcChecked()).isTrue();
        assertThat(checked.content()).isEqualTo(Arrays.copyOf(FrontierSat.beacon(), 202));
    }

    @Test
    @Requirement("SAT-020")
    void frameWithoutAValidTrailerIsTakenAsItCame() {
        final var stripped = Arrays.copyOf(FrontierSat.beacon(), 202);
        final var damaged = FrontierSat.beacon();
        damaged[100] ^= 0x01;

        assertThat(FrameCheck.CRC32C_TRAILER.check(stripped)).satisfies(checked -> {
            assertThat(checked.crcChecked()).isFalse();
            assertThat(checked.content()).isEqualTo(stripped);
        });
        assertThat(FrameCheck.CRC32C_TRAILER.check(damaged).content()).hasSize(206);
        assertThat(FrameCheck.CRC32C_TRAILER.check(new byte[4]).crcChecked()).isFalse();
        assertThat(FrameCheck.CRC32C_TRAILER.check(new byte[3]).content()).hasSize(3);
    }

    @Test
    void satelliteWithoutAFrameCheckKeepsItsWholeFrame() {
        assertThat(FrameCheck.NONE.check(new byte[] {1, 2})).satisfies(checked -> {
            assertThat(checked.crcChecked()).isNull();
            assertThat(checked.content()).containsExactly(1, 2);
        });
        assertThat(DATABASES.frameCheck(FrontierSat.SATELLITE)).isEqualTo(FrameCheck.CRC32C_TRAILER);
        assertThat(DATABASES.frameCheck(SimulatorTelemetry.SATELLITE)).isEqualTo(FrameCheck.NONE);
    }

    private static Optional<EngineeringValue> engineering(String name) {
        return BEACON.values().stream().filter(value -> value.parameter().name().equals(name)).findFirst()
                .flatMap(ParameterValue::engineering);
    }

    private static double number(String name) {
        return ((FloatValue) engineering(name).orElseThrow()).value();
    }
}
