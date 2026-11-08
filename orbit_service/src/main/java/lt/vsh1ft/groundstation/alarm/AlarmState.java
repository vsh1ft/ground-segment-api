package lt.vsh1ft.groundstation.alarm;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Where an alarm is in its lifecycle: TRIGGERED (out of limits, not acknowledged), ACKNOWLEDGED "
        + "(out of limits, acknowledged), RETURNED (back in limits, waiting for acknowledgement), CLEARED (finished)")
public enum AlarmState {
    TRIGGERED, ACKNOWLEDGED, RETURNED, CLEARED
}
