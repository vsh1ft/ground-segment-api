package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record ValuesQuery(
        @Parameter(description = "Most values to return, at most 100; 20 if omitted", example = "20")
        @Min(1) @Max(100) Integer limit,

        @Parameter(description = "Only values archived before the one with this id; pass `nextBefore` from the "
                + "previous response to get the next page", example = "4711")
        @Positive Long before,

        @Parameter(description = "Only values generated at or after this time (UTC, ISO-8601)",
                example = "2026-11-08T10:00:00Z")
        Instant from,

        @Parameter(description = "Only values generated before this time (UTC, ISO-8601)",
                example = "2026-11-08T11:00:00Z")
        Instant to) {

    private static final int DEFAULT_LIMIT = 20;

    public ValuesQuery {
        if (limit == null) {
            limit = DEFAULT_LIMIT;
        }
    }
}
