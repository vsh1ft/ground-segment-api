package lt.vsh1ft.groundstation.api;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lt.vsh1ft.groundstation.station.Station;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.util.UriComponentsBuilder;

@RequestMapping("/stations")
@Tag(name = "Stations", description = "Ground stations that passes are predicted for")
public interface StationsApi {

    String INVALID_STATION_EXAMPLE = """
            {"title": "Bad Request", "status": 400, "detail": "Invalid request content.", "instance": "/stations",
             "errors": [{"field": "latitudeDeg", "message": "must be between -90 and 90"},
                        {"field": "name", "message": "must not be blank"}]}
            """;
    String UNKNOWN_STATION_EXAMPLE = """
            {"title": "Not Found", "status": 404,
             "detail": "No station with ID 3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42",
             "instance": "/stations/3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42"}
            """;
    String NAME_TAKEN_EXAMPLE = """
            {"title": "Conflict", "status": 409, "detail": "A station named 'Vilnius' already exists",
             "instance": "/stations"}
            """;

    @GetMapping
    @Operation(summary = "List stations", description = "Returns every station, sorted by name.")
    @ApiResponse(responseCode = "200", description = "All stations; empty if none have been created")
    List<Station> all();

    @GetMapping("/{id}")
    @Operation(summary = "Get a station")
    @ApiResponse(responseCode = "200", description = "The station")
    @ApiResponse(responseCode = "404", description = "No station with this ID",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = UNKNOWN_STATION_EXAMPLE)))
    Station get(@Parameter(description = "Station ID", example = "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42")
                @PathVariable UUID id);

    @PostMapping
    @Operation(summary = "Create a station",
            description = "Creates a station and returns its generated ID. Station names are unique.")
    @ApiResponse(responseCode = "201", description = "Station created",
            headers = @Header(name = "Location", description = "URL of the new station",
                    schema = @Schema(type = "string", format = "uri")))
    @ApiResponse(responseCode = "400", description = "A field is missing or invalid; `errors` names each one",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = INVALID_STATION_EXAMPLE)))
    @ApiResponse(responseCode = "409", description = "Another station already has this name",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = NAME_TAKEN_EXAMPLE)))
    ResponseEntity<IdResponse> create(@Valid @RequestBody StationRequest request, UriComponentsBuilder uri);

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Replace a station", description = "Replaces every field of an existing station.")
    @ApiResponse(responseCode = "204", description = "Station replaced")
    @ApiResponse(responseCode = "400", description = "A field is missing or invalid; `errors` names each one",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = INVALID_STATION_EXAMPLE)))
    @ApiResponse(responseCode = "404", description = "No station with this ID",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = UNKNOWN_STATION_EXAMPLE)))
    @ApiResponse(responseCode = "409", description = "Another station already has this name",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = NAME_TAKEN_EXAMPLE)))
    void update(@Parameter(description = "Station ID", example = "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42")
                @PathVariable UUID id,
                @Valid @RequestBody StationRequest request);

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a station")
    @ApiResponse(responseCode = "204", description = "Station deleted")
    @ApiResponse(responseCode = "404", description = "No station with this ID",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = UNKNOWN_STATION_EXAMPLE)))
    void delete(@Parameter(description = "Station ID", example = "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42")
                @PathVariable UUID id);
}
