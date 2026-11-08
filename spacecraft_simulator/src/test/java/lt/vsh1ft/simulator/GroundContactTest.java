package lt.vsh1ft.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GroundContactTest {

    private static final Map<String, String> VILNIUS = Map.of(
            "SIMULATOR_TLE_FILE", "spacecraft_simulator/gsapi-sim.tle",
            "SIMULATOR_STATION_LATITUDE", "54.6872",
            "SIMULATOR_STATION_LONGITUDE", "25.2797",
            "SIMULATOR_STATION_ALTITUDE", "112",
            "SIMULATOR_STATION_MIN_ELEVATION", "10");

    @Test
    void withoutATleTheSimulatorHasNoGroundContact() {
        assertThat(GroundContact.from(Map.of())).isEmpty();
    }

    @Test
    void tleAndStationAreReadFromTheEnvironmentWithTheDefaultOrekitData() {
        assertThat(GroundContact.from(VILNIUS)).contains(new GroundContact(
                Path.of("spacecraft_simulator/gsapi-sim.tle"), 54.6872, 25.2797, 112, 10, Path.of("orekit-data")));
    }

    @Test
    void orekitDataCanBeMovedElsewhere() {
        final var environment = new HashMap<>(VILNIUS);
        environment.put("SIMULATOR_OREKIT_DATA", "/data/orekit");

        assertThat(GroundContact.from(environment)).hasValueSatisfying(contact ->
                assertThat(contact.orekitData()).isEqualTo(Path.of("/data/orekit")));
    }

    @Test
    void stationIsRequiredWithATle() {
        final var environment = new HashMap<>(VILNIUS);
        environment.remove("SIMULATOR_STATION_MIN_ELEVATION");

        assertThatIllegalArgumentException().isThrownBy(() -> GroundContact.from(environment))
                .withMessage("SIMULATOR_STATION_MIN_ELEVATION is required when SIMULATOR_TLE_FILE is set");
    }

    @ParameterizedTest
    @CsvSource({"90, 180, 90", "-90, -180, -5"})
    void everyLimitIsAccepted(double latitude, double longitude, double minElevation) {
        assertThat(new GroundContact(Path.of("a.tle"), latitude, longitude, 0, minElevation, Path.of("o"))
                .latitudeDeg()).isEqualTo(latitude);
    }

    @ParameterizedTest
    @CsvSource({"90.000001, 0, 10", "-90.000001, 0, 10", "0, 180.000001, 10", "0, -180.000001, 10",
            "0, 0, 90.000001", "0, 0, -5.000001"})
    void valuesBeyondTheLimitsAreRejected(double latitude, double longitude, double minElevation) {
        assertThatIllegalArgumentException().isThrownBy(() -> new GroundContact(Path.of("a.tle"), latitude,
                longitude, 0, minElevation, Path.of("o")));
    }
}
