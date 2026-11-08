package lt.vsh1ft.groundstation.tmtc;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Who received a frame: SATNOGS, a station of the SatNOGS network; OWN, one of your stations")
public enum ReceptionSource {
    SATNOGS, OWN
}
