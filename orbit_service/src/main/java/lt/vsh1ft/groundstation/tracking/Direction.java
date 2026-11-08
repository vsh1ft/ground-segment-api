package lt.vsh1ft.groundstation.tracking;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DOWNLINK: the frequency to receive on; UPLINK: the frequency to transmit on")
public enum Direction {
    DOWNLINK, UPLINK
}
