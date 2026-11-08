package lt.vsh1ft.groundstation.contact;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Frame counts of one pass of the TM link's satellite over its station")
public record LinkReportResponse(
        @Schema(description = "Predicted start of the pass, UTC", example = "2026-11-08T18:02:27.7Z")
        Instant passStart,

        @Schema(description = "Predicted end of the pass, UTC", example = "2026-11-08T18:10:07.1Z")
        Instant passEnd,

        @Schema(description = "Frames that passed the frame error control check", example = "1712")
        long framesReceived,

        @Schema(description = "Frames missing from the master channel frame count sequence: corrupted or never "
                + "received", example = "128")
        long framesLost,

        @Schema(description = "Frames received but failing the frame error control check; part of framesLost",
                example = "41")
        long framesFailedCheck,

        @Schema(description = "Received idle frames (virtual channel 7)", example = "1301")
        long idleFrames,

        @Schema(description = "framesLost / (framesReceived + framesLost); 0 before any frame", example = "0.0696")
        double lossRatio,

        @Schema(description = "When these counts were written, UTC; the pass end once the pass is over",
                example = "2026-11-08T18:10:07.1Z")
        Instant updatedAt) {

    static LinkReportResponse of(LinkReport report) {
        return new LinkReportResponse(report.passStart(), report.passEnd(), report.framesReceived(),
                report.framesLost(), report.framesFailedCheck(), report.idleFrames(), report.lossRatio(),
                report.updatedAt());
    }
}
