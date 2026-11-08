package lt.vsh1ft.groundstation.tracking;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The track of a satellite over a station: where to point and which frequencies to use")
public record TrackResponse(
        @Schema(description = "NORAD catalog number", example = "25544")
        int satellite,

        @Schema(description = "Station ID", example = "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42")
        UUID station,

        @Schema(description = "Epoch of the element set the track was computed from, UTC",
                example = "2026-10-31T04:14:04Z")
        Instant elementsEpoch,

        @Schema(description = "Time between points, ISO-8601", example = "PT1S", type = "string")
        Duration step,

        @Schema(description = "The frequencies each point gives a corrected value for")
        List<Frequency> frequencies,

        @Schema(description = "Points from `from`, one every `step`")
        List<TrackPoint> points) {

    public TrackResponse {
        frequencies = List.copyOf(frequencies);
        points = List.copyOf(points);
    }
}
