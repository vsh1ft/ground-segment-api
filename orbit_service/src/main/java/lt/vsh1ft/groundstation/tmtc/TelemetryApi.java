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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Telemetry", description = "Telemetry received from spacecraft over TM links")
public interface TelemetryApi {

    @GetMapping("/satellites/{noradId}/telemetry/packets")
    @Operation(summary = "List the archived telemetry packets of a satellite",
            description = "Returns the CCSDS space packets received from the satellite, most recently archived "
                    + "first, optionally filtered by generation time, APID and PUS message type. To page, pass "
                    + "`nextBefore` from the response as `before`; pages stay stable while new packets arrive. Each "
                    + "packet is stored exactly as received and laid out as described in the TM link ICD. The "
                    + "satellite doesn't have to be in the catalog: a simulated spacecraft, or one not launched yet, "
                    + "has telemetry but no orbital elements.")
    @ApiResponse(responseCode = "200", description = "The packets; empty if none match")
    @ApiResponse(responseCode = "400", description = "A parameter is invalid; `errors` names each one",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    TmPacketsResponse packets(
            @Parameter(description = "NORAD catalog number the TM link is assigned to", example = "99999")
            @PathVariable @Positive int noradId,
            @Valid @ParameterObject PacketsQuery query);
}
