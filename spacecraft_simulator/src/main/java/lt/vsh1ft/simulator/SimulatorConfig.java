package lt.vsh1ft.simulator;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

record SimulatorConfig(int port, Duration housekeepingPeriod, Duration orbitPeriod, double frameDropRate,
                       double frameCorruptRate) {

    static final String PORT = "SIMULATOR_TM_PORT";
    static final String HOUSEKEEPING_PERIOD = "SIMULATOR_HOUSEKEEPING_PERIOD";
    static final String ORBIT_PERIOD = "SIMULATOR_ORBIT_PERIOD";
    static final String FRAME_DROP_RATE = "SIMULATOR_FRAME_DROP_RATE";
    static final String FRAME_CORRUPT_RATE = "SIMULATOR_FRAME_CORRUPT_RATE";
    private static final int DEFAULT_PORT = 10015;
    private static final Duration DEFAULT_HOUSEKEEPING_PERIOD = Duration.ofSeconds(1);
    private static final Duration DEFAULT_ORBIT_PERIOD = Duration.ofMinutes(92);

    SimulatorConfig {
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException(PORT + " must be between 0 and 65535, was " + port);
        }
        requirePositive(HOUSEKEEPING_PERIOD, housekeepingPeriod);
        requirePositive(ORBIT_PERIOD, orbitPeriod);
        requireRate(FRAME_DROP_RATE, frameDropRate);
        requireRate(FRAME_CORRUPT_RATE, frameCorruptRate);
    }

    static SimulatorConfig from(Map<String, String> environment) {
        return new SimulatorConfig(
                Optional.ofNullable(environment.get(PORT)).map(Integer::parseInt).orElse(DEFAULT_PORT),
                duration(environment, HOUSEKEEPING_PERIOD, DEFAULT_HOUSEKEEPING_PERIOD),
                duration(environment, ORBIT_PERIOD, DEFAULT_ORBIT_PERIOD),
                rate(environment, FRAME_DROP_RATE),
                rate(environment, FRAME_CORRUPT_RATE));
    }

    private static Duration duration(Map<String, String> environment, String name, Duration defaultValue) {
        return Optional.ofNullable(environment.get(name)).map(Duration::parse).orElse(defaultValue);
    }

    private static double rate(Map<String, String> environment, String name) {
        return Optional.ofNullable(environment.get(name)).map(Double::parseDouble).orElse(0.0);
    }

    private static void requireRate(String name, double rate) {
        if (rate < 0 || rate > 1) {
            throw new IllegalArgumentException(name + " must be between 0 and 1, was " + rate);
        }
    }

    private static void requirePositive(String name, Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive, was " + duration);
        }
    }
}
