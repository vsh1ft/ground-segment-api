package lt.vsh1ft.groundstation.pass;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

class GroundStationTest {

    @Test
    void stationRejectsLatitudeAbove90Degrees() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GroundStation("Bad", 91.0, 0.0, 0.0));
    }

    @Test
    void stationRejectsLongitudeBelowMinus180Degrees() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GroundStation("Bad", 0.0, -180.5, 0.0));
    }

    @Test
    void stationAcceptsThePoles() {
        assertThatNoException().isThrownBy(() -> new GroundStation("South Pole", -90.0, 0.0, 2835.0));
    }
}
