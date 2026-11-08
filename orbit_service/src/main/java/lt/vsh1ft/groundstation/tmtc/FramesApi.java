package lt.vsh1ft.groundstation.tmtc;

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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Frames", description = "Frames received from real satellites, by SatNOGS stations or your own")
public interface FramesApi {

    @GetMapping("/satellites/{noradId}/frames")
    @Operation(summary = "List the received frames of a satellite",
            description = "Returns distinct frames, most recently first received first, each with every station "
                    + "that received it. A frame of a satellite with a mission database is decoded once, when it is "
                    + "first received, into the parameter archive. Filter by `source` or by one of your stations; "
                    + "to page, pass `nextBefore` as `before`.")
    @ApiResponse(responseCode = "200", description = "The frames; empty if none match")
    @ApiResponse(responseCode = "400", description = "A parameter is invalid",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    FramesResponse frames(
            @Parameter(description = "NORAD catalog number", example = "69015")
            @PathVariable @Positive int noradId,
            @Valid @ParameterObject FramesQuery query);

    @PostMapping("/satellites/{noradId}/frames")
    @Operation(summary = "Add a frame your own station received",
            description = "Stores a frame from your decoder with the station and time it was received. A frame "
                    + "already received, by SatNOGS or another station, gets one more reception; the same frame from "
                    + "the same station at the same time adds nothing.")
    @ApiResponse(responseCode = "201", description = "A frame the service hadn't seen, with its receptions")
    @ApiResponse(responseCode = "200", description = "A frame already received, with its receptions")
    @ApiResponse(responseCode = "400", description = "The frame isn't 1 to 2048 bytes in hex, or a field is missing",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "The station is unknown or the satellite is not in the catalog",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<FrameResponse> upload(
            @Parameter(description = "NORAD catalog number", example = "69015")
            @PathVariable @Positive int noradId,
            @Valid @RequestBody FrameUpload upload);
}
