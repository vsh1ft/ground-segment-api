package lt.vsh1ft.groundstation.catalog;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

public record ElementsQuery(
        @Parameter(description = "Zero-based page number; 0 if omitted", example = "0")
        @PositiveOrZero Integer page,

        @Parameter(description = "Element sets per page, at most 100; 20 if omitted", example = "20")
        @Min(1) @Max(100) Integer size) {

    private static final int FIRST_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    public ElementsQuery {
        if (page == null) {
            page = FIRST_PAGE;
        }
        if (size == null) {
            size = DEFAULT_SIZE;
        }
    }
}
