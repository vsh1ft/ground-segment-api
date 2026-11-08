package lt.vsh1ft.groundstation.tracking;

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

@Tag(name = "Tracking", description = "Where to point the antenna and which frequency to use, second by second")
public interface TrackApi {

    @GetMapping("/satellites/{noradId}/track")
    @Operation(summary = "Track a satellite from a station",
            description = "Returns azimuth, geometric elevation, range and range rate at every step of the window, "
                    + "below the horizon too, and the Doppler-corrected frequencies of the satellite's active "
                    + "transmitters from the SatNOGS DB: the downlink to receive on and the uplink to transmit on. "
                    + "`downlinkHz` and `uplinkHz` replace the transmitters with frequencies of your own. Take "
                    + "`from` and `duration` from a pass of `/passes`; at most 10 000 points.")
    @ApiResponse(responseCode = "200", description = "The track")
    @ApiResponse(responseCode = "400", description = "A parameter is invalid, or the window has too many points",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = """
                            {"title": "Bad Request", "status": 400,
                             "detail": "A track of PT24H at a step of PT1S has 86401 points; at most 10000 are \
                            allowed, so ask for a shorter track or a longer step",
                             "instance": "/satellites/25544/track"}
                            """)))
    @ApiResponse(responseCode = "404", description = "The satellite is not in the catalog or the station is unknown",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    TrackResponse track(
            @Parameter(description = "NORAD catalog number", example = "25544")
            @PathVariable @Positive int noradId,
            @Valid @ParameterObject TrackQuery query);
}
