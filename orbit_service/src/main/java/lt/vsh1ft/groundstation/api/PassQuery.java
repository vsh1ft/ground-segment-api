package lt.vsh1ft.groundstation.api;

import java.time.Duration;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;

public record PassQuery(
        @Parameter(description = "NORAD catalog number of the satellite", example = "25544")
        @NotNull @Positive Integer satellite,

        @Parameter(description = "ID of the station to predict passes over",
                example = "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42")
        @NotNull UUID station,

        @Parameter(description = "Length of the forecast window as an ISO-8601 duration; 1 day (P1D) if omitted, "
                + "at most 7 days", example = "PT12H")
        @DurationMin(nanos = 0, inclusive = false) @DurationMax(days = 7) Duration duration) {

    private static final Duration DEFAULT_DURATION = Duration.ofDays(1);

    public PassQuery {
        if (duration == null) {
            duration = DEFAULT_DURATION;
        }
    }
}
