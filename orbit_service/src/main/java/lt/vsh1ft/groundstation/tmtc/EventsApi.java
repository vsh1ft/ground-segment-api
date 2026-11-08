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

@Tag(name = "Events", description = "Events reported by spacecraft (PUS service 5)")
public interface EventsApi {

    @GetMapping("/satellites/{noradId}/events")
    @Operation(summary = "List the event log of a satellite",
            description = "Returns the events the spacecraft reported, most recently archived first. The severity "
                    + "comes from the packet's PUS subtype; name, description and auxiliary values come from the "
                    + "satellite's mission database. An event the database can't decode is still listed, without a "
                    + "name. Filter by `minSeverity` and by generation time; to page, pass `nextBefore` as `before`.")
    @ApiResponse(responseCode = "200", description = "The events; empty if none match")
    @ApiResponse(responseCode = "400", description = "A parameter is invalid",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    EventsResponse events(
            @Parameter(description = "NORAD catalog number the TM link is assigned to", example = "99999")
            @PathVariable @Positive int noradId,
            @Valid @ParameterObject EventsQuery query);
}
