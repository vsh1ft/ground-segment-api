package lt.vsh1ft.groundstation.tmtc;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Frames of a satellite, most recently first received first")
public record FramesResponse(
        List<FrameResponse> frames,

        @Schema(description = "Pass as `before` to get the next, older page; null when there are no more frames",
                example = "4711", nullable = true)
        Long nextBefore) {

    public FramesResponse {
        frames = List.copyOf(frames);
    }
}
