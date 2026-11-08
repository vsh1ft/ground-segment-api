package lt.vsh1ft.groundstation.alarm;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lt.vsh1ft.xtce.MonitoringLevel;

@Schema(description = "An alarm on one parameter: raised when its value left the limits of the mission database, "
        + "closed once the value is back in limits and an operator has acknowledged it")
public record AlarmResponse(
        @Schema(description = "Alarm identifier", example = "3")
        long id,

        @Schema(description = "Parameter name", example = "BATTERY_VOLTAGE")
        String parameter,

        @Schema(description = "Most severe level the value has reached while the alarm is open",
                example = "CRITICAL", allowableValues = {"WARNING", "CRITICAL"})
        MonitoringLevel level,

        @Schema(description = "Lifecycle state", example = "RETURNED")
        AlarmState state,

        @Schema(description = "Generation time of the value that raised the alarm, UTC",
                example = "2026-11-08T09:00:01Z")
        Instant triggeredAt,

        @Schema(description = "Engineering value that raised the alarm", example = "7.199")
        double triggerValue,

        @Schema(description = "Generation time of the value that brought the parameter back in limits; null "
                + "while it is out of limits", example = "2026-11-08T09:04:30Z", nullable = true)
        Instant returnedAt,

        @Schema(description = "When an operator acknowledged the alarm, ground time; null if not acknowledged",
                example = "2026-11-08T09:30:00Z", nullable = true)
        Instant acknowledgedAt,

        @Schema(description = "When the alarm was closed; null while it is open", example = "2026-11-08T09:30:00Z",
                nullable = true)
        Instant clearedAt) {

    static AlarmResponse of(Alarm alarm) {
        return new AlarmResponse(alarm.id(), alarm.parameter(), alarm.level(), alarm.state(), alarm.triggeredAt(),
                alarm.triggerValue(), alarm.returnedAt(), alarm.acknowledgedAt(), alarm.clearedAt());
    }
}
