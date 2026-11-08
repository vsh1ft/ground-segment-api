package lt.vsh1ft.groundstation.alarm;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The open alarms of a satellite")
public record AlarmsResponse(
        @Schema(description = "Open alarms, most recently raised first")
        List<AlarmResponse> alarms) {
}
