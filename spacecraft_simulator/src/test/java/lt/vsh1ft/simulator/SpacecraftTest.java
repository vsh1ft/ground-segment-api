package lt.vsh1ft.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SpacecraftTest {

    private static final Instant START = Instant.parse("2026-11-08T00:00:00Z");

    @ParameterizedTest
    @CsvSource({
            "0, 7400, 150",
            "23, 7700, 350",
            "46, 7400, 150",
            "69, 7100, -50"
    })
    void batteryAndTemperatureFollowTheSunAroundTheOrbit(long minutes, int millivolts, int deciCelsius) {
        final var report = new Spacecraft(START).housekeeping(START.plus(Duration.ofMinutes(minutes)));

        assertThat(report).isEqualTo(new HousekeepingReport(millivolts, deciCelsius, SpacecraftMode.NOMINAL));
    }
}
