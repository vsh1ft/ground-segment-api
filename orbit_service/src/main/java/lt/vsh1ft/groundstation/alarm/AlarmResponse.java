package lt.vsh1ft.groundstation.alarm;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lt.vsh1ft.xtce.MonitoringLevel;

@Schema(description = "An alarm: raised when a parameter value left the limits of the mission database, or when "
        + "telemetry stopped during a planned pass; closed once the condition is over and an operator has "
        + "acknowledged it")
public record AlarmResponse(
        @Schema(description = "Alarm identifier", example = "3")
        long id,

        @Schema(description = "What raised the alarm: LIMIT, a parameter value outside its limits, or "
                + "LOSS_OF_SIGNAL, no telemetry during a planned pass", example = "LIMIT")
        AlarmKind kind,

        @Schema(description = "Parameter name; null for a loss of signal", example = "BATTERY_VOLTAGE",
                nullable = true)
        String parameter,

        @Schema(description = "Most severe level the value has reached while the alarm is open",
                example = "CRITICAL", allowableValues = {"WARNING", "CRITICAL"})
        MonitoringLevel level,

        @Schema(description = "Lifecycle state", example = "RETURNED")
        AlarmState state,

        @Schema(description = "Generation time of the value that raised the alarm, or the ground time a loss of "
                + "signal was noticed, UTC",
                example = "2026-11-08T09:00:01Z")
        Instant triggeredAt,

        @Schema(description = "Engineering value that raised the alarm; null for a loss of signal", example = "7.199",
                nullable = true)
        Double triggerValue,

        @Schema(description = "Generation time of the value that brought the parameter back in limits, or when "
                + "telemetry came back or the pass ended after a loss of signal; null while the alarm's condition "
                + "lasts", example = "2026-11-08T09:04:30Z", nullable = true)
        Instant returnedAt,

        @Schema(description = "When an operator acknowledged the alarm, ground time; null if not acknowledged",
                example = "2026-11-08T09:30:00Z", nullable = true)
        Instant acknowledgedAt,

        @Schema(description = "When the alarm was closed; null while it is open", example = "2026-11-08T09:30:00Z",
                nullable = true)
        Instant clearedAt) {

    static AlarmResponse of(Alarm alarm) {
        return new AlarmResponse(alarm.id(), alarm.kind(), alarm.parameter(), alarm.level(), alarm.state(), alarm.triggeredAt(),
                alarm.triggerValue(), alarm.returnedAt(), alarm.acknowledgedAt(), alarm.clearedAt());
    }
}
