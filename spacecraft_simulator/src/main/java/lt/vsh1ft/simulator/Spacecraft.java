package lt.vsh1ft.simulator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class Spacecraft {

    static final int BATTERY_LOW_BELOW_MILLIVOLTS = 7150;
    static final int BATTERY_RECOVERED_FROM_MILLIVOLTS = 7250;
    static final int TEMPERATURE_HIGH_ABOVE_DECI_CELSIUS = 330;
    static final int TEMPERATURE_NORMAL_FROM_DECI_CELSIUS = 300;
    private static final int MEAN_BATTERY_VOLTAGE_MILLIVOLTS = 7400;
    private static final int BATTERY_SWING_MILLIVOLTS = 300;
    private static final int MEAN_TEMPERATURE_DECI_CELSIUS = 150;
    private static final int TEMPERATURE_SWING_DECI_CELSIUS = 200;

    private final Instant start;
    private final Duration orbitPeriod;
    private SpacecraftMode mode = SpacecraftMode.NOMINAL;
    private boolean batteryLow;
    private boolean temperatureHigh;

    Spacecraft(Instant start, Duration orbitPeriod) {
        this.start = start;
        this.orbitPeriod = orbitPeriod;
    }

    Step step(Instant now) {
        final var orbitPhase = 2 * Math.PI * Duration.between(start, now).toMillis() / orbitPeriod.toMillis();
        final var sunlight = Math.sin(orbitPhase);
        final int battery = (int) Math.round(MEAN_BATTERY_VOLTAGE_MILLIVOLTS + BATTERY_SWING_MILLIVOLTS * sunlight);
        final int temperature = (int) Math.round(MEAN_TEMPERATURE_DECI_CELSIUS
                + TEMPERATURE_SWING_DECI_CELSIUS * sunlight);
        final var events = new ArrayList<EventReport>();
        watchBattery(battery, events);
        watchTemperature(temperature, events);
        return new Step(new HousekeepingReport(battery, temperature, mode), events);
    }

    private void watchBattery(int battery, List<EventReport> events) {
        if (!batteryLow && battery < BATTERY_LOW_BELOW_MILLIVOLTS) {
            batteryLow = true;
            events.add(EventReport.of(EventDefinition.BATTERY_LOW, battery));
            mode = SpacecraftMode.SAFE;
            events.add(EventReport.of(EventDefinition.SAFE_MODE_ENTERED));
        } else if (batteryLow && battery >= BATTERY_RECOVERED_FROM_MILLIVOLTS) {
            batteryLow = false;
            events.add(EventReport.of(EventDefinition.BATTERY_RECOVERED, battery));
            mode = SpacecraftMode.NOMINAL;
            events.add(EventReport.of(EventDefinition.NOMINAL_MODE_RESUMED));
        }
    }

    private void watchTemperature(int temperature, List<EventReport> events) {
        if (!temperatureHigh && temperature > TEMPERATURE_HIGH_ABOVE_DECI_CELSIUS) {
            temperatureHigh = true;
            events.add(EventReport.of(EventDefinition.TEMPERATURE_HIGH, temperature));
        } else if (temperatureHigh && temperature <= TEMPERATURE_NORMAL_FROM_DECI_CELSIUS) {
            temperatureHigh = false;
            events.add(EventReport.of(EventDefinition.TEMPERATURE_NORMAL, temperature));
        }
    }

    record Step(HousekeepingReport housekeeping, List<EventReport> events) {

        Step {
            events = List.copyOf(events);
        }
    }
}
