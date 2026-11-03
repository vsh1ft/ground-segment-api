package lt.vsh1ft.groundstation.pass;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

class GroundStationTest {

    @Test
    void stationRejectsLatitudeAbove90Degrees() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GroundStation("Bad", 91.0, 0.0, 0.0, 0.0));
    }

    @Test
    void stationRejectsLongitudeBelowMinus180Degrees() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GroundStation("Bad", 0.0, -180.5, 0.0, 0.0));
    }

    @Test
    void stationRejectsMinimumElevationBelowMinus5Degrees() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GroundStation("Bad", 0.0, 0.0, 0.0, -5.1));
    }

    @Test
    void stationRejectsMinimumElevationAbove90Degrees() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GroundStation("Bad", 0.0, 0.0, 0.0, 90.1));
    }

    @Test
    void stationAcceptsSlightlyNegativeMinimumElevation() {
        assertThatNoException().isThrownBy(() -> new GroundStation("Hilltop", 0.0, 0.0, 500.0, -5.0));
    }

    @Test
    void stationAcceptsThePoles() {
        assertThatNoException().isThrownBy(() -> new GroundStation("South Pole", -90.0, 0.0, 2835.0, 0.0));
    }
}
