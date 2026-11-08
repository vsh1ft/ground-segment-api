package lt.vsh1ft.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SimulatorConfigTest {

    private static final Duration SECOND = Duration.ofSeconds(1);
    private static final Duration ORBIT = Duration.ofMinutes(92);

    @Test
    void defaultsArePort10015OneReportPerSecondAndARealisticOrbit() {
        assertThat(SimulatorConfig.from(Map.of())).isEqualTo(new SimulatorConfig(10015, SECOND, ORBIT, 0, 0));
    }

    @Test
    void environmentOverridesTheDefaults() {
        final var environment = Map.of("SIMULATOR_TM_PORT", "20015", "SIMULATOR_HOUSEKEEPING_PERIOD", "PT5S",
                "SIMULATOR_ORBIT_PERIOD", "PT10M", "SIMULATOR_FRAME_DROP_RATE", "0.05",
                "SIMULATOR_FRAME_CORRUPT_RATE", "0.03");

        assertThat(SimulatorConfig.from(environment))
                .isEqualTo(new SimulatorConfig(20015, Duration.ofSeconds(5), Duration.ofMinutes(10), 0.05, 0.03));
    }

    @Test
    void portAboveSixteenBitsIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SimulatorConfig(65_536, SECOND, ORBIT, 0, 0));
    }

    @Test
    void zeroHousekeepingPeriodIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SimulatorConfig(10015, Duration.ZERO, ORBIT, 0, 0));
    }

    @Test
    void negativeOrbitPeriodIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SimulatorConfig(10015, SECOND,
                Duration.ofMinutes(-1), 0, 0));
    }

    @Test
    void frameRatesFromZeroToOneAreAccepted() {
        assertThat(new SimulatorConfig(10015, SECOND, ORBIT, 1, 1).frameDropRate()).isEqualTo(1);
    }

    @Test
    void frameRatesOutsideZeroToOneAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SimulatorConfig(10015, SECOND, ORBIT, -0.01, 0))
                .withMessage("SIMULATOR_FRAME_DROP_RATE must be between 0 and 1, was -0.01");
        assertThatIllegalArgumentException().isThrownBy(() -> new SimulatorConfig(10015, SECOND, ORBIT, 0, 1.01))
                .withMessage("SIMULATOR_FRAME_CORRUPT_RATE must be between 0 and 1, was 1.01");
    }
}
