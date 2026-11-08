package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record PacketsQuery(
        @Parameter(description = "Most packets to return, at most 100; 20 if omitted", example = "20")
        @Min(1) @Max(100) Integer limit,

        @Parameter(description = "Only packets archived before the one with this id; pass `nextBefore` from the "
                + "previous response to get the next page", example = "4711")
        @Positive Long before,

        @Parameter(description = "Only packets generated at or after this time (UTC, ISO-8601)",
                example = "2026-11-08T10:00:00Z")
        Instant from,

        @Parameter(description = "Only packets generated before this time (UTC, ISO-8601)",
                example = "2026-11-08T11:00:00Z")
        Instant to,

        @Parameter(description = "Only packets from this application process ID", example = "100")
        @Min(0) @Max(2047) Integer apid,

        @Parameter(description = "Only packets of this PUS service type", example = "3")
        @Min(0) @Max(255) Integer service,

        @Parameter(description = "Only packets of this PUS message subtype", example = "25")
        @Min(0) @Max(255) Integer subtype) {

    private static final int DEFAULT_LIMIT = 20;

    public PacketsQuery {
        if (limit == null) {
            limit = DEFAULT_LIMIT;
        }
    }
}
