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
        assertThat(SimulatorConfig.from(Map.of())).isEqualTo(new SimulatorConfig(10015, SECOND, ORBIT));
    }

    @Test
    void environmentOverridesTheDefaults() {
        final var environment = Map.of("SIMULATOR_TM_PORT", "20015", "SIMULATOR_HOUSEKEEPING_PERIOD", "PT5S",
                "SIMULATOR_ORBIT_PERIOD", "PT10M");

        assertThat(SimulatorConfig.from(environment))
                .isEqualTo(new SimulatorConfig(20015, Duration.ofSeconds(5), Duration.ofMinutes(10)));
    }

    @Test
    void portAboveSixteenBitsIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SimulatorConfig(65_536, SECOND, ORBIT));
    }

    @Test
    void zeroHousekeepingPeriodIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SimulatorConfig(10015, Duration.ZERO, ORBIT));
    }

    @Test
    void negativeOrbitPeriodIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SimulatorConfig(10015, SECOND,
                Duration.ofMinutes(-1)));
    }
}
