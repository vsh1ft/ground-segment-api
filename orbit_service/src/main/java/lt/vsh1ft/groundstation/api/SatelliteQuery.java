package lt.vsh1ft.groundstation.api;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SatelliteQuery(
        @Parameter(description = "Part of the satellite name, case-insensitive, or an exact NORAD catalog number; "
                + "every satellite if omitted", example = "iss")
        @Size(max = 100) String search,

        @Parameter(description = "Zero-based page number; 0 if omitted", example = "0")
        @PositiveOrZero Integer page,

        @Parameter(description = "Satellites per page, at most 100; 20 if omitted", example = "20")
        @Min(1) @Max(100) Integer size) {

    private static final int FIRST_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    public SatelliteQuery {
        if (page == null) {
            page = FIRST_PAGE;
        }
        if (size == null) {
            size = DEFAULT_SIZE;
        }
    }
}
