package lt.vsh1ft.simulator;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

record SimulatorConfig(int port, Duration housekeepingPeriod) {

    static final String PORT = "SIMULATOR_TM_PORT";
    static final String HOUSEKEEPING_PERIOD = "SIMULATOR_HOUSEKEEPING_PERIOD";
    private static final int DEFAULT_PORT = 10015;
    private static final Duration DEFAULT_HOUSEKEEPING_PERIOD = Duration.ofSeconds(1);

    SimulatorConfig {
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException(PORT + " must be between 0 and 65535, was " + port);
        }
        if (housekeepingPeriod.isNegative() || housekeepingPeriod.isZero()) {
            throw new IllegalArgumentException(HOUSEKEEPING_PERIOD + " must be positive, was " + housekeepingPeriod);
        }
    }

    static SimulatorConfig from(Map<String, String> environment) {
        return new SimulatorConfig(
                Optional.ofNullable(environment.get(PORT)).map(Integer::parseInt).orElse(DEFAULT_PORT),
                Optional.ofNullable(environment.get(HOUSEKEEPING_PERIOD)).map(Duration::parse)
                        .orElse(DEFAULT_HOUSEKEEPING_PERIOD));
    }
}
