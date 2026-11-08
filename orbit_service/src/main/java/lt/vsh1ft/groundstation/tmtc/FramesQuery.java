package lt.vsh1ft.groundstation.tmtc;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record FramesQuery(
        @Parameter(description = "Most frames to return, at most 100; 20 if omitted", example = "20")
        @Min(1) @Max(100) Integer limit,

        @Parameter(description = "Only frames archived before the one with this id; pass `nextBefore` from the "
                + "previous response to get the next page", example = "4711")
        @Positive Long before,

        @Parameter(description = "Only frames with at least one reception of this source", example = "OWN")
        ReceptionSource source,

        @Parameter(description = "Only frames this station of yours received",
                example = "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42")
        UUID station) {

    private static final int DEFAULT_LIMIT = 20;

    public FramesQuery {
        if (limit == null) {
            limit = DEFAULT_LIMIT;
        }
    }
}
