package lt.vsh1ft.simulator;

import static lt.vsh1ft.simulator.EventDefinition.BATTERY_LOW;
import static lt.vsh1ft.simulator.EventDefinition.BATTERY_RECOVERED;
import static lt.vsh1ft.simulator.EventDefinition.NOMINAL_MODE_RESUMED;
import static lt.vsh1ft.simulator.EventDefinition.SAFE_MODE_ENTERED;
import static lt.vsh1ft.simulator.EventDefinition.TEMPERATURE_HIGH;
import static lt.vsh1ft.simulator.EventDefinition.TEMPERATURE_NORMAL;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SpacecraftTest {

    private static final Instant START = Instant.parse("2026-11-08T00:00:00Z");
    private static final Duration ORBIT = Duration.ofMinutes(92);

    @ParameterizedTest
    @CsvSource({
            "0, 7400, 150",
            "23, 7700, 350",
            "46, 7400, 150",
            "69, 7100, -50"
    })
    void batteryAndTemperatureFollowTheSunAroundTheOrbit(long minutes, int millivolts, int deciCelsius) {
        final var report = new Spacecraft(START, ORBIT).step(START.plus(Duration.ofMinutes(minutes))).housekeeping();

        assertThat(report.batteryVoltageMillivolts()).isEqualTo(millivolts);
        assertThat(report.temperatureDeciCelsius()).isEqualTo(deciCelsius);
    }

    @Test
    void oneOrbitRaisesEveryEventOnceInOrderWithHysteresis() {
        final var spacecraft = new Spacecraft(START, ORBIT);
        final var events = new ArrayList<EventReport>();
        for (long second = 0; second <= ORBIT.toSeconds(); second++) {
            events.addAll(spacecraft.step(START.plusSeconds(second)).events());
        }

        assertThat(events).extracting(EventReport::definition).containsExactly(TEMPERATURE_HIGH,
                TEMPERATURE_NORMAL, BATTERY_LOW, SAFE_MODE_ENTERED, BATTERY_RECOVERED, NOMINAL_MODE_RESUMED);
        assertThat(events.get(0).auxiliaryValue()).isEqualTo(331);
        assertThat(events.get(1).auxiliaryValue()).isEqualTo(300);
        assertThat(events.get(2).auxiliaryValue()).isEqualTo(7149);
        assertThat(events.get(4).auxiliaryValue()).isEqualTo(7250);
    }

    @Test
    void lowBatteryPutsTheSpacecraftInSafeModeUntilItRecovers() {
        final var spacecraft = new Spacecraft(START, ORBIT);
        final List<SpacecraftMode> modes = new ArrayList<>();
        for (long minute = 0; minute <= 92; minute += 1) {
            modes.add(spacecraft.step(START.plus(Duration.ofMinutes(minute))).housekeeping().mode());
        }

        assertThat(modes.get(60)).isEqualTo(SpacecraftMode.NOMINAL);
        assertThat(modes.get(69)).isEqualTo(SpacecraftMode.SAFE);
        assertThat(modes.get(80)).isEqualTo(SpacecraftMode.SAFE);
        assertThat(modes.get(86)).isEqualTo(SpacecraftMode.NOMINAL);
    }

    @Test
    void shorterOrbitPeriodSpeedsEverythingUp() {
        final var spacecraft = new Spacecraft(START, Duration.ofMinutes(4));

        assertThat(spacecraft.step(START.plusSeconds(60)).housekeeping().batteryVoltageMillivolts()).isEqualTo(7700);
    }
}
