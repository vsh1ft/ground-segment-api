package lt.vsh1ft.groundstation.tmtc;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

@Tag(name = "Parameters", description = "Telemetry parameters decoded with the satellite's XTCE mission database")
public interface ParametersApi {

    @GetMapping("/satellites/{noradId}/parameters")
    @Operation(summary = "List the parameters of a satellite",
            description = "Returns every parameter defined in the satellite's mission database (an XTCE file), "
                    + "including the CCSDS and PUS header fields, in the order the database defines them.")
    @ApiResponse(responseCode = "200", description = "The parameters")
    @ApiResponse(responseCode = "400", description = "The NORAD ID is not positive",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No mission database for the satellite",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = """
                            {"title": "Not Found", "status": 404,
                             "detail": "No mission database for satellite 25544",
                             "instance": "/satellites/25544/parameters"}
                            """)))
    List<ParameterResponse> parameters(
            @Parameter(description = "NORAD catalog number the TM link is assigned to", example = "99999")
            @PathVariable @Positive int noradId);

    @GetMapping("/satellites/{noradId}/parameters/{name}/values")
    @Operation(summary = "List the archived values of a parameter",
            description = "Returns the values decoded from the satellite's packets, most recently archived first, "
                    + "with the raw value as sent and the engineering value after calibration. Filter by generation "
                    + "time with `from` (inclusive) and `to` (exclusive); to page, pass `nextBefore` as `before`.")
    @ApiResponse(responseCode = "200", description = "The values; empty if none match")
    @ApiResponse(responseCode = "400", description = "A parameter is invalid; `errors` names each one",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No mission database for the satellite, or no such parameter",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = """
                            {"title": "Not Found", "status": 404,
                             "detail": "No parameter SOLAR_CURRENT in the mission database of satellite 99999",
                             "instance": "/satellites/99999/parameters/SOLAR_CURRENT/values"}
                            """)))
    ParameterValuesResponse values(
            @Parameter(description = "NORAD catalog number the TM link is assigned to", example = "99999")
            @PathVariable @Positive int noradId,
            @Parameter(description = "Parameter name from the mission database", example = "BATTERY_VOLTAGE")
            @PathVariable String name,
            @Valid @ParameterObject ValuesQuery query);
}
