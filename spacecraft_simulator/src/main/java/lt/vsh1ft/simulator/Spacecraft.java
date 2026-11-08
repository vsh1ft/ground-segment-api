package lt.vsh1ft.simulator;

import java.time.Duration;
import java.time.Instant;

final class Spacecraft {

    static final Duration ORBIT_PERIOD = Duration.ofMinutes(92);
    private static final int MEAN_BATTERY_VOLTAGE_MILLIVOLTS = 7400;
    private static final int BATTERY_SWING_MILLIVOLTS = 300;
    private static final int MEAN_TEMPERATURE_DECI_CELSIUS = 150;
    private static final int TEMPERATURE_SWING_DECI_CELSIUS = 200;

    private final Instant start;

    Spacecraft(Instant start) {
        this.start = start;
    }

    HousekeepingReport housekeeping(Instant now) {
        final var orbitPhase = 2 * Math.PI * Duration.between(start, now).toMillis() / ORBIT_PERIOD.toMillis();
        final var sunlight = Math.sin(orbitPhase);
        return new HousekeepingReport(
                (int) Math.round(MEAN_BATTERY_VOLTAGE_MILLIVOLTS + BATTERY_SWING_MILLIVOLTS * sunlight),
                (int) Math.round(MEAN_TEMPERATURE_DECI_CELSIUS + TEMPERATURE_SWING_DECI_CELSIUS * sunlight),
                SpacecraftMode.NOMINAL);
    }
}
