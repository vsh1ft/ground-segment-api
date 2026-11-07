package lt.vsh1ft.groundstation.pass;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "Passes", description = "Predicted satellite passes over a ground station")
public interface PassesApi {

    @GetMapping("/passes")
    @Operation(
            summary = "Predict passes",
            description = "Predicts every pass of a catalog satellite over the station, starting now. A pass begins when "
                    + "the satellite rises above the station's minimum elevation and ends when it sets below it. "
                    + "The prediction uses the newest stored orbital elements, which a background job refreshes from "
                    + "CelesTrak every few hours; `elementsEpoch` tells how old they are.")
    @ApiResponse(responseCode = "200", description = "Passes in time order; empty if there are none in the window")
    @ApiResponse(responseCode = "400", description = "A parameter is missing or invalid; `errors` names each one",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = """
                            {"title": "Bad Request", "status": 400, "detail": "Invalid request content.",
                             "instance": "/passes",
                             "errors": [{"field": "duration", "message": "must be shorter than or equal to 7 days"}]}
                            """)))
    @ApiResponse(responseCode = "404", description = "Unknown station, or a satellite that is not in the catalog",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = """
                            {"title": "Not Found", "status": 404,
                             "detail": "No satellite with NORAD ID 99999 in the catalog", "instance": "/passes"}
                            """)))
    @ApiResponse(responseCode = "503", description = "No orbital elements are stored for the satellite",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    PassesResponse passes(@Valid @ParameterObject PassQuery query);
}
