package lt.vsh1ft.groundstation.api;

import java.time.Duration;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;

public record PassQuery(
        @NotNull @Positive Integer satellite,
        @NotNull UUID station,
        @DurationMin(nanos = 0, inclusive = false) @DurationMax(days = 7) Duration duration) {

    private static final Duration DEFAULT_DURATION = Duration.ofDays(1);

    public PassQuery {
        if (duration == null) {
            duration = DEFAULT_DURATION;
        }
    }
}
