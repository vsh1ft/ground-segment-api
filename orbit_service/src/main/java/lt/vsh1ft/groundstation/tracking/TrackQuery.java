package lt.vsh1ft.groundstation.tracking;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;

public record TrackQuery(
        @Parameter(description = "ID of the station to track from", example = "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42")
        @NotNull UUID station,

        @Parameter(description = "Time of the first point (UTC, ISO-8601); now if omitted",
                example = "2026-11-01T00:41:16Z")
        Instant from,

        @Parameter(description = "Length of the track as an ISO-8601 duration; 15 minutes (PT15M) if omitted, at "
                + "most 1 day", example = "PT9M")
        @DurationMin(nanos = 0, inclusive = false) @DurationMax(days = 1) Duration duration,

        @Parameter(description = "Time between points as an ISO-8601 duration; 1 second (PT1S) if omitted, from "
                + "100 ms to 1 hour", example = "PT1S")
        @DurationMin(millis = 100) @DurationMax(hours = 1) Duration step,

        @Parameter(description = "A downlink frequency to correct, Hz; replaces the satellite's SatNOGS transmitters",
                example = "437000000")
        @Positive Long downlinkHz,

        @Parameter(description = "An uplink frequency to correct, Hz; replaces the satellite's SatNOGS transmitters",
                example = "145900000")
        @Positive Long uplinkHz) {

    private static final Duration DEFAULT_DURATION = Duration.ofMinutes(15);
    private static final Duration DEFAULT_STEP = Duration.ofSeconds(1);

    public TrackQuery {
        if (duration == null) {
            duration = DEFAULT_DURATION;
        }
        if (step == null) {
            step = DEFAULT_STEP;
        }
    }

    long points() {
        return duration.dividedBy(step) + 1;
    }

    boolean requestsFrequencies() {
        return downlinkHz != null || uplinkHz != null;
    }
}
