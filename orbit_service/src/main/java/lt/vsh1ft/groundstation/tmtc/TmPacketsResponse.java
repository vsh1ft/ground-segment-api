package lt.vsh1ft.groundstation.tmtc;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One page of archived telemetry packets of a satellite, newest first")
public record TmPacketsResponse(
        @Schema(description = "The packets, most recently archived first")
        List<TmPacketResponse> packets,

        @Schema(description = "Pass as `before` to get the next, older page; null when there are no more packets",
                example = "4692", nullable = true)
        Long nextBefore) {
}
