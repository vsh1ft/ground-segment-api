package lt.vsh1ft.simulator;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

record SimulatorConfig(int port, Duration housekeepingPeriod, Duration orbitPeriod) {

    static final String PORT = "SIMULATOR_TM_PORT";
    static final String HOUSEKEEPING_PERIOD = "SIMULATOR_HOUSEKEEPING_PERIOD";
    static final String ORBIT_PERIOD = "SIMULATOR_ORBIT_PERIOD";
    private static final int DEFAULT_PORT = 10015;
    private static final Duration DEFAULT_HOUSEKEEPING_PERIOD = Duration.ofSeconds(1);
    private static final Duration DEFAULT_ORBIT_PERIOD = Duration.ofMinutes(92);

    SimulatorConfig {
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException(PORT + " must be between 0 and 65535, was " + port);
        }
        requirePositive(HOUSEKEEPING_PERIOD, housekeepingPeriod);
        requirePositive(ORBIT_PERIOD, orbitPeriod);
    }

    static SimulatorConfig from(Map<String, String> environment) {
        return new SimulatorConfig(
                Optional.ofNullable(environment.get(PORT)).map(Integer::parseInt).orElse(DEFAULT_PORT),
                duration(environment, HOUSEKEEPING_PERIOD, DEFAULT_HOUSEKEEPING_PERIOD),
                duration(environment, ORBIT_PERIOD, DEFAULT_ORBIT_PERIOD));
    }

    private static Duration duration(Map<String, String> environment, String name, Duration defaultValue) {
        return Optional.ofNullable(environment.get(name)).map(Duration::parse).orElse(defaultValue);
    }

    private static void requirePositive(String name, Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive, was " + duration);
        }
    }
}
