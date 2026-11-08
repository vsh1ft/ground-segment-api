package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A distinct frame received from a satellite, with every reception of it")
public record FrameResponse(
        @Schema(description = "Archive identifier; higher means first received later", example = "4712")
        long id,

        @Schema(description = "When the frame was first received, UTC", example = "2026-11-08T19:03:34Z")
        Instant firstReceivedAt,

        @Schema(description = "Deepest container of the satellite's mission database the frame matched; null if it "
                + "wasn't decoded", example = "BEACON_EXTENDED_V5", nullable = true)
        String container,

        @Schema(description = "When the spacecraft made the frame, from its on-board time; null if the frame has "
                + "none", example = "2026-11-08T13:51:24.338Z", nullable = true)
        Instant generationTime,

        @Schema(description = "True when the frame arrived with a valid CRC, which was then removed; false when it "
                + "arrived without one, as some stations' decoders check and strip it; null if the satellite has no "
                + "frame check", example = "true", nullable = true)
        Boolean crcChecked,

        @Schema(description = "Frame length in bytes, without the CRC", example = "202")
        int length,

        @Schema(description = "The frame without its CRC, hex", example = "C2A28A0021435453310100C23307193C")
        String raw,

        @Schema(description = "Every reception of the frame, first one first")
        List<Reception> receptions) {

    public FrameResponse {
        receptions = List.copyOf(receptions);
    }

    @Schema(description = "One time a station received the frame")
    public record Reception(
            @Schema(description = "SATNOGS or OWN", example = "SATNOGS")
            ReceptionSource source,

            @Schema(description = "Station name", example = "Dtec Station")
            String station,

            @Schema(description = "ID of your station; null for SatNOGS stations", nullable = true)
            UUID stationId,

            @Schema(description = "Station latitude, degrees", example = "25.12", nullable = true)
            Double latitudeDeg,

            @Schema(description = "Station longitude, degrees", example = "55.377", nullable = true)
            Double longitudeDeg,

            @Schema(description = "Station altitude, metres", example = "32", nullable = true)
            Double altitudeM,

            @Schema(description = "SatNOGS observation ID; null for your stations", example = "15386644",
                    nullable = true)
            Long observation,

            @Schema(description = "When the station received the frame, UTC", example = "2026-11-08T19:03:34Z")
            Instant receivedAt) {
    }
}
