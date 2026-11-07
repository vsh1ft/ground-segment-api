package lt.vsh1ft.groundstation.radio;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lt.vsh1ft.groundstation.catalog.SatellitesApi;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Transmitters", description = "Radio transmitters of catalog satellites, from the SatNOGS DB")
public interface TransmittersApi {

    @GetMapping("/satellites/{noradId}/transmitters")
    @Operation(summary = "List the transmitters of a catalog satellite",
            description = "Returns the satellite's transmitters, transceivers and transponders as listed in the "
                    + "SatNOGS DB, sorted by downlink frequency. They are synced once a day, after a catalog refresh.")
    @ApiResponse(responseCode = "200", description = "The satellite's transmitters; empty if SatNOGS lists none")
    @ApiResponse(responseCode = "400", description = "A parameter is invalid",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "The satellite is not in the catalog",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = SatellitesApi.NOT_IN_CATALOG_EXAMPLE)))
    List<TransmitterResponse> transmitters(
            @Parameter(description = "NORAD catalog number", example = "25544") @PathVariable @Positive int noradId,
            @Parameter(description = "Only transmitters with this status; all if omitted", example = "ACTIVE")
            @RequestParam(required = false) TransmitterStatus status);
}
