package lt.vsh1ft.groundstation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lt.vsh1ft.groundstation.satellite.SatelliteSummary;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.util.UriComponentsBuilder;

@RequestMapping("/satellites")
@Tag(name = "Satellites", description = "The catalog of satellites that passes can be predicted for")
public interface SatellitesApi {

    String NOT_IN_CATALOG_EXAMPLE = """
            {"title": "Not Found", "status": 404, "detail": "No satellite with NORAD ID 25544 in the catalog",
             "instance": "/satellites/25544"}
            """;

    @GetMapping
    @Operation(summary = "Search the catalog", description = "Returns one page of catalog satellites, sorted by name.")
    @ApiResponse(responseCode = "200", description = "One page of satellites with paging metadata")
    @ApiResponse(responseCode = "400", description = "A parameter is invalid; `errors` names each one",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = """
                            {"title": "Bad Request", "status": 400, "detail": "Invalid request content.",
                             "instance": "/satellites",
                             "errors": [{"field": "size", "message": "must be less than or equal to 100"}]}
                            """)))
    PagedModel<SatelliteSummary> search(@Valid @ParameterObject SatelliteQuery query);

    @GetMapping("/{noradId}")
    @Operation(summary = "Get a catalog satellite")
    @ApiResponse(responseCode = "200", description = "The satellite")
    @ApiResponse(responseCode = "404", description = "The satellite is not in the catalog",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = NOT_IN_CATALOG_EXAMPLE)))
    SatelliteSummary get(@Parameter(description = "NORAD catalog number", example = "25544")
                         @PathVariable @Positive int noradId);

    @PostMapping("/{noradId}")
    @Operation(summary = "Add a satellite to the catalog",
            description = "Downloads the satellite's current orbital elements from CelesTrak and adds it to the "
                    + "catalog with the name and international designator CelesTrak publishes.")
    @ApiResponse(responseCode = "201", description = "Satellite added",
            headers = @Header(name = "Location", description = "URL of the catalog satellite",
                    schema = @Schema(type = "string", format = "uri")))
    @ApiResponse(responseCode = "404", description = "CelesTrak has no orbit data for this NORAD ID",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = """
                            {"title": "Not Found", "status": 404, "detail": "No orbit data for NORAD ID 99999",
                             "instance": "/satellites/99999"}
                            """)))
    @ApiResponse(responseCode = "409", description = "The satellite is already in the catalog",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = """
                            {"title": "Conflict", "status": 409, "detail": "NORAD ID 25544 is already in the catalog",
                             "instance": "/satellites/25544"}
                            """)))
    @ApiResponse(responseCode = "503", description = "CelesTrak can't be reached",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<Void> add(@Parameter(description = "NORAD catalog number", example = "25544")
                             @PathVariable @Positive int noradId,
                             UriComponentsBuilder uri);

    @DeleteMapping("/{noradId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a satellite from the catalog",
            description = "Removes the satellite together with its stored orbital elements.")
    @ApiResponse(responseCode = "204", description = "Satellite removed")
    @ApiResponse(responseCode = "404", description = "The satellite is not in the catalog",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = NOT_IN_CATALOG_EXAMPLE)))
    void delete(@Parameter(description = "NORAD catalog number", example = "25544")
                @PathVariable @Positive int noradId);
}
