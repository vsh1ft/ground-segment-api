package lt.vsh1ft.groundstation.alarm;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Tag(name = "Alarms", description = "Out-of-limits alarms raised from the limits in the mission database")
public interface AlarmsApi {

    @GetMapping("/satellites/{noradId}/alarms")
    @Operation(summary = "List the open alarms of a satellite",
            description = "Every received value is checked against the warning and critical limits of its type in "
                    + "the mission database. A value out of limits raises an alarm, or escalates the parameter's "
                    + "open alarm to CRITICAL. An alarm stays open until its value is back in limits and an "
                    + "operator has acknowledged it.")
    @ApiResponse(responseCode = "200", description = "The open alarms; empty if there are none")
    @ApiResponse(responseCode = "400", description = "The NORAD ID is invalid",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    AlarmsResponse alarms(
            @Parameter(description = "NORAD catalog number the TM link is assigned to", example = "99999")
            @PathVariable @Positive int noradId);

    @PostMapping("/satellites/{noradId}/alarms/{id}/acknowledge")
    @Operation(summary = "Acknowledge an alarm",
            description = "Marks the alarm as seen. An alarm already back in limits is cleared; one still out of "
                    + "limits stays open as ACKNOWLEDGED until its value returns. Acknowledging an acknowledged "
                    + "alarm changes nothing. If the alarm escalates to CRITICAL later, it needs a new "
                    + "acknowledgement.")
    @ApiResponse(responseCode = "200", description = "The alarm after the acknowledgement")
    @ApiResponse(responseCode = "400", description = "A path parameter is invalid",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "The satellite has no alarm with this ID",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The alarm is already cleared, or changed during the request",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    AlarmResponse acknowledge(
            @Parameter(description = "NORAD catalog number the TM link is assigned to", example = "99999")
            @PathVariable @Positive int noradId,
            @Parameter(description = "Alarm identifier", example = "3")
            @PathVariable @Positive long id);
}
