package lt.vsh1ft.groundstation.pass;

import static org.assertj.core.api.Assertions.assertThat;

import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CompassPointTest {

    @ParameterizedTest
    @CsvSource({
            "0.0, N",
            "11.24, N",
            "11.25, NNE",
            "45.0, NE",
            "90.0, E",
            "168.74, SSE",
            "180.0, S",
            "247.5, WSW",
            "338.21, NNW",
            "348.74, NNW",
            "348.75, N",
            "359.99, N"
    })
    @Requirement("PASS-040")
    void azimuthMapsToTheNearestOfSixteenCompassPoints(double azimuthDeg, CompassPoint expected) {
        assertThat(CompassPoint.of(azimuthDeg)).isEqualTo(expected);
    }
}
