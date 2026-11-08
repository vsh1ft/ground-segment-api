package lt.vsh1ft.groundstation.alarm;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What raised an alarm: LIMIT (a parameter value outside its limits) or LOSS_OF_SIGNAL (no "
        + "telemetry during a planned pass)")
public enum AlarmKind {
    LIMIT, LOSS_OF_SIGNAL
}
