package lt.vsh1ft.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SimulatorConfigTest {

    @Test
    void defaultsArePort10015AndOneReportPerSecond() {
        assertThat(SimulatorConfig.from(Map.of())).isEqualTo(new SimulatorConfig(10015, Duration.ofSeconds(1)));
    }

    @Test
    void environmentOverridesTheDefaults() {
        final var environment = Map.of("SIMULATOR_TM_PORT", "20015", "SIMULATOR_HOUSEKEEPING_PERIOD", "PT5S");

        assertThat(SimulatorConfig.from(environment)).isEqualTo(new SimulatorConfig(20015, Duration.ofSeconds(5)));
    }

    @Test
    void portAboveSixteenBitsIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SimulatorConfig(65_536, Duration.ofSeconds(1)));
    }

    @Test
    void zeroHousekeepingPeriodIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SimulatorConfig(10015, Duration.ZERO));
    }
}
