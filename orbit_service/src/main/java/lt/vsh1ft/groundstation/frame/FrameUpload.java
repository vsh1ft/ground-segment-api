package lt.vsh1ft.groundstation.frame;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "A frame one of your stations received")
public record FrameUpload(
        @Schema(description = "ID of your station that received the frame",
                example = "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42")
        @NotNull UUID station,

        @Schema(description = "When the station received the frame, UTC", example = "2026-11-08T19:03:34.120Z")
        @NotNull Instant receivedAt,

        @Schema(description = "The whole frame as the decoder produced it, hex, 1 to 2048 bytes",
                example = "C2A28A0021435453310100C23307193C")
        @NotNull @Pattern(regexp = "(?:[0-9A-Fa-f]{2}){1,2048}", message = "must be 1 to 2048 bytes in hex")
        String frame) {
}
