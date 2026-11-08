package lt.vsh1ft.groundstation.alarm;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What raised an alarm: LIMIT (a parameter value outside its limits), LOSS_OF_SIGNAL (no "
        + "telemetry during a planned pass) or LINK_QUALITY (too many transfer frames lost in a pass)")
public enum AlarmKind {
    LIMIT, LOSS_OF_SIGNAL, LINK_QUALITY
}
