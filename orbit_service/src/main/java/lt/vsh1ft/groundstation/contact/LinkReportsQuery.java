package lt.vsh1ft.groundstation.contact;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record LinkReportsQuery(
        @Parameter(description = "Most reports to return, at most 100; 20 if omitted", example = "20")
        @Min(1) @Max(100) Integer limit) {

    private static final int DEFAULT_LIMIT = 20;

    public LinkReportsQuery {
        if (limit == null) {
            limit = DEFAULT_LIMIT;
        }
    }
}
