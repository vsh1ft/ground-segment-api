package lt.vsh1ft.groundstation.api;

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
            description = "Predicts every pass of the satellite over the station, starting now. A pass begins when "
                    + "the satellite rises above the station's minimum elevation and ends when it sets below it. "
                    + "Orbital elements are downloaded from CelesTrak at most every couple of hours; if CelesTrak "
                    + "can't be reached, the last stored elements are used.")
    @ApiResponse(responseCode = "200", description = "Passes in time order; empty if there are none in the window")
    @ApiResponse(responseCode = "400", description = "A parameter is missing or invalid; `errors` names each one",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = """
                            {"title": "Bad Request", "status": 400, "detail": "Invalid request content.",
                             "instance": "/passes",
                             "errors": [{"field": "duration", "message": "must be shorter than or equal to 7 days"}]}
                            """)))
    @ApiResponse(responseCode = "404", description = "Unknown station, or a satellite CelesTrak has no orbit data for",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = """
                            {"title": "Not Found", "status": 404, "detail": "No orbit data for NORAD ID 99999",
                             "instance": "/passes"}
                            """)))
    @ApiResponse(responseCode = "503", description = "CelesTrak can't be reached and no orbit data is stored yet",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    PassesResponse passes(@Valid @ParameterObject PassQuery query);
}
