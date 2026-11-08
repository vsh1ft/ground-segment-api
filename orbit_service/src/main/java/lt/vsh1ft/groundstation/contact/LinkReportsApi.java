package lt.vsh1ft.groundstation.contact;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Link reports", description = "How well the TM link worked in each pass, from the transfer frame counters")
public interface LinkReportsApi {

    @GetMapping("/satellites/{noradId}/link-reports")
    @Operation(summary = "List the link reports of a satellite",
            description = "Returns one report per pass, newest pass first. The report of a pass in progress is "
                    + "updated every 10 seconds and once more when the pass ends. Frames received outside a planned "
                    + "pass are not in any report.")
    @ApiResponse(responseCode = "200", description = "The reports; empty if no frames were received in a pass")
    @ApiResponse(responseCode = "400", description = "A parameter is invalid",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    LinkReportsResponse reports(
            @Parameter(description = "NORAD catalog number the TM link is assigned to", example = "99999")
            @PathVariable @Positive int noradId,
            @Valid @ParameterObject LinkReportsQuery query);
}
