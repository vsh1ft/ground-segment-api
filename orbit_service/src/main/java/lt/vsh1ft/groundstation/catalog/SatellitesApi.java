package lt.vsh1ft.groundstation.catalog;

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
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/{noradId}/elements")
    @Operation(summary = "Get the stored orbital elements of a catalog satellite",
            description = "Returns one page of the satellite's stored element sets, newest epoch first. These are "
                    + "the CCSDS OMM mean elements that passes are predicted from with SGP4; the background refresh "
                    + "adds a set whenever CelesTrak publishes a newer epoch.")
    @ApiResponse(responseCode = "200", description = "One page of element sets with paging metadata")
    @ApiResponse(responseCode = "400", description = "A parameter is invalid; `errors` names each one",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "The satellite is not in the catalog",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = NOT_IN_CATALOG_EXAMPLE)))
    PagedModel<ElementSet> elements(@Parameter(description = "NORAD catalog number", example = "25544")
                                    @PathVariable @Positive int noradId,
                                    @Valid @ParameterObject ElementsQuery query);

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

    @PostMapping(value = "/{noradId}/elements", consumes = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Upload orbital elements as a TLE",
            description = "Stores a three-line TLE (a name and two element lines) as an element set, for a "
                    + "satellite CelesTrak doesn't list yet, such as a new launch, or for an operator's own orbit "
                    + "solution. A satellite outside the catalog is added under the TLE's name. Passes use the "
                    + "element set with the newest epoch, whether downloaded or uploaded.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Three-line TLE", required = true,
            content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE, examples = @ExampleObject(value = """
                    GSAPI-SIM
                    1 99999U 26999A   26312.50000000  .00000000  00000+0  00000+0 0  9997
                    2 99999  97.5000 312.0000 0012000  90.0000 270.0000 15.20000000    14
                    """)))
    @ApiResponse(responseCode = "201", description = "Satellite added to the catalog with this element set",
            headers = @Header(name = "Location", description = "URL of the catalog satellite",
                    schema = @Schema(type = "string", format = "uri")))
    @ApiResponse(responseCode = "200", description = "Element set stored for a satellite already in the catalog")
    @ApiResponse(responseCode = "400", description = "Not a valid TLE, or a TLE of another satellite",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = """
                            {"title": "Bad Request", "status": 400,
                             "detail": "The TLE is for NORAD ID 25544, not 99999",
                             "instance": "/satellites/99999/elements"}
                            """)))
    ResponseEntity<ElementSet> upload(@Parameter(description = "NORAD catalog number", example = "99999")
                                      @PathVariable @Positive int noradId,
                                      @RequestBody String tle,
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
