package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;
import java.util.HexFormat;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A CCSDS space packet with a PUS-C telemetry secondary header, as received over the TM link")
public record TmPacketResponse(
        @Schema(description = "Archive identifier; higher means received later", example = "42")
        long id,

        @Schema(description = "Application process ID: the on-board application that sent the packet", example = "100")
        int apid,

        @Schema(description = "Per-APID packet counter, 0 to 16383; a gap means packets were lost", example = "5")
        int sequenceCount,

        @Schema(description = "PUS service type; 3 = housekeeping", example = "3")
        int serviceType,

        @Schema(description = "PUS message subtype; 25 = housekeeping parameter report", example = "25")
        int messageSubtype,

        @Schema(description = "When the spacecraft generated the packet, from its on-board time, UTC",
                example = "2026-11-08T10:15:30.5Z")
        Instant generationTime,

        @Schema(description = "When the ground received the packet, UTC", example = "2026-11-08T10:15:30.512Z")
        Instant receptionTime,

        @Schema(description = "The whole packet as received, hex",
                example = "0864C00500122003190001000012345678800001020304FB2C")
        String raw) {

    static TmPacketResponse from(StoredTmPacket packet) {
        return new TmPacketResponse(packet.id(), packet.apid(), packet.sequenceCount(), packet.serviceType(),
                packet.messageSubtype(), packet.generationTime(), packet.receptionTime(),
                HexFormat.of().withUpperCase().formatHex(packet.raw()));
    }
}
