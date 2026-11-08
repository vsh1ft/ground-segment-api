package lt.vsh1ft.groundstation.satnogs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.List;

import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;

class SatnogsImportPropertiesTest {

    private static final Duration HOUR = Duration.ofHours(1);

    @Test
    @Requirement("SAT-010")
    void noSatellitesMeansAnEmptyListAndASettleTimeOfZeroIsAllowed() {
        final var properties = new SatnogsImportProperties(null, HOUR, HOUR, Duration.ZERO);

        assertThat(properties.satellites()).isEmpty();
        assertThat(properties.settle()).isZero();
        assertThat(new SatnogsImportProperties(List.of(69015), Duration.ofNanos(1), Duration.ofNanos(1), HOUR)
                .satellites()).containsExactly(69015);
    }

    @Test
    @Requirement("SAT-010")
    void intervalAndLookBackMustBePositiveAndSettleNotNegative() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SatnogsImportProperties(null, Duration.ZERO, HOUR,
                HOUR)).withMessage("groundstation.satnogs.import.interval must be positive, was PT0S");
        assertThatIllegalArgumentException().isThrownBy(() -> new SatnogsImportProperties(null, HOUR, null, HOUR))
                .withMessage("groundstation.satnogs.import.look-back must be positive, was null");
        assertThatIllegalArgumentException().isThrownBy(() -> new SatnogsImportProperties(null, HOUR,
                Duration.ofSeconds(-1), HOUR));
        assertThatIllegalArgumentException().isThrownBy(() -> new SatnogsImportProperties(null, HOUR, HOUR,
                Duration.ofNanos(-1))).withMessage("groundstation.satnogs.import.settle must be zero or more, was "
                + "PT-0.000000001S");
        assertThatIllegalArgumentException().isThrownBy(() -> new SatnogsImportProperties(null, HOUR, HOUR, null));
    }
}
