package lt.vsh1ft.groundstation.pass;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;

class GroundStationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @Requirement("STN-030")
    void stationRejectsLatitudeAbove90Degrees() {
        assertThat(invalidFields(new GroundStation("Bad", 91.0, 0.0, 0.0, 0.0))).containsExactly("latitudeDeg");
    }

    @Test
    @Requirement("STN-030")
    void stationRejectsLongitudeBelowMinus180Degrees() {
        assertThat(invalidFields(new GroundStation("Bad", 0.0, -180.5, 0.0, 0.0))).containsExactly("longitudeDeg");
    }

    @Test
    @Requirement("STN-030")
    void stationRejectsMinimumElevationBelowMinus5Degrees() {
        assertThat(invalidFields(new GroundStation("Bad", 0.0, 0.0, 0.0, -5.1))).containsExactly("minElevationDeg");
    }

    @Test
    @Requirement("STN-030")
    void stationRejectsMinimumElevationAbove90Degrees() {
        assertThat(invalidFields(new GroundStation("Bad", 0.0, 0.0, 0.0, 90.1))).containsExactly("minElevationDeg");
    }

    @Test
    @Requirement("STN-030")
    void stationAcceptsSlightlyNegativeMinimumElevation() {
        assertThat(invalidFields(new GroundStation("Hilltop", 0.0, 0.0, 500.0, -5.0))).isEmpty();
    }

    @Test
    @Requirement("STN-030")
    void stationAcceptsThePoles() {
        assertThat(invalidFields(new GroundStation("South Pole", -90.0, 0.0, 2835.0, 0.0))).isEmpty();
    }

    private Set<String> invalidFields(GroundStation station) {
        return validator.validate(station).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
