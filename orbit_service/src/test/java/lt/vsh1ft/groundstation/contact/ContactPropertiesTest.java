package lt.vsh1ft.groundstation.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;

class ContactPropertiesTest {

    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);

    @Test
    @Requirement({"CON-010", "CON-030"})
    void smallestPositiveDurationsAreAccepted() {
        final var properties = new ContactProperties(Duration.ofNanos(1), Duration.ofNanos(1));

        assertThat(properties.silenceThreshold()).isEqualTo(Duration.ofNanos(1));
        assertThat(properties.planInterval()).isEqualTo(Duration.ofNanos(1));
    }

    @Test
    @Requirement("CON-030")
    void silenceThresholdMustBePositive() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ContactProperties(Duration.ZERO, TEN_MINUTES))
                .withMessage("groundstation.contact.silence-threshold must be positive, was PT0S");
        assertThatIllegalArgumentException().isThrownBy(() -> new ContactProperties(Duration.ofSeconds(-1),
                TEN_MINUTES));
        assertThatIllegalArgumentException().isThrownBy(() -> new ContactProperties(null, TEN_MINUTES))
                .withMessage("groundstation.contact.silence-threshold must be positive, was null");
    }

    @Test
    @Requirement("CON-010")
    void planIntervalMustBePositive() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ContactProperties(TEN_MINUTES, Duration.ZERO))
                .withMessage("groundstation.contact.plan-interval must be positive, was PT0S");
    }
}
