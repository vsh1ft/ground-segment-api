package lt.vsh1ft.groundstation.alarm;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Where an alarm is in its lifecycle: TRIGGERED (condition lasts, not acknowledged), "
        + "ACKNOWLEDGED (condition lasts, acknowledged), RETURNED (condition over, waiting for acknowledgement), "
        + "CLEARED (finished)")
public enum AlarmState {
    TRIGGERED, ACKNOWLEDGED, RETURNED, CLEARED
}
