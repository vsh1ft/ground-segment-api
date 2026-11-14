package lt.vsh1ft.groundstation.frame;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lt.vsh1ft.groundstation.shared.CursorPage;

@Schema(description = "Frames of a satellite, most recently first received first")
public record FramesResponse(
        List<FrameResponse> frames,

        @Schema(description = "Pass as `before` to get the next, older page; null when there are no more frames",
                example = "4711", nullable = true)
        Long nextBefore) {

    public FramesResponse {
        frames = List.copyOf(frames);
    }

    static FramesResponse of(CursorPage<ReceivedFrame> page) {
        return new FramesResponse(page.map(FrameResponse::from), page.nextBefore());
    }
}
