package lt.vsh1ft.groundstation.contact;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Whether a packet was received during a planned pass of the link's station: IN_PASS, "
        + "OUTSIDE_PASS, or UNKNOWN when no pass could be predicted")
public enum Visibility {
    IN_PASS,
    OUTSIDE_PASS,
    UNKNOWN
}
