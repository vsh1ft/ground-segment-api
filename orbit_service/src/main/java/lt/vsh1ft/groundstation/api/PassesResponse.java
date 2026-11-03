package lt.vsh1ft.groundstation.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lt.vsh1ft.groundstation.pass.NoradId;
import lt.vsh1ft.groundstation.pass.Pass;
import lt.vsh1ft.groundstation.station.Station;

@Schema(description = "Predicted passes of one satellite over one station")
public record PassesResponse(
        @Schema(description = "NORAD catalog number of the satellite", example = "25544")
        int satellite,

        @Schema(description = "ID of the station", example = "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42")
        UUID station,

        @Schema(description = "Passes in time order")
        List<PassView> passes) {

    static PassesResponse of(NoradId satellite, Station station, List<Pass> passes) {
        return new PassesResponse(satellite.value(), station.id(), passes.stream().map(PassView::of).toList());
    }

    @Schema(description = "One pass: from rising above the station's minimum elevation to setting below it")
    public record PassView(
            @Schema(description = "When the satellite rises above the minimum elevation (UTC, millisecond precision)",
                    example = "2026-11-02T19:56:46.676Z")
            Instant start,

            @Schema(description = "Highest point of the pass")
            Peak peak,

            @Schema(description = "When the satellite sets below the minimum elevation (UTC, millisecond precision)",
                    example = "2026-11-02T19:59:38.080Z")
            Instant end) {

        static PassView of(Pass pass) {
            return new PassView(
                    truncatedToMillis(pass.start()),
                    new Peak(truncatedToMillis(pass.peak().time()), roundedToHundredths(pass.peak().elevationDeg())),
                    truncatedToMillis(pass.end()));
        }
    }

    @Schema(description = "Highest point of a pass")
    public record Peak(
            @Schema(description = "Time of the highest elevation (UTC, millisecond precision)",
                    example = "2026-11-02T19:58:12.161Z")
            Instant time,

            @Schema(description = "Highest elevation above the horizon in degrees, rounded to two decimals",
                    example = "6.2")
            double elevationDeg) {
    }

    private static Instant truncatedToMillis(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MILLIS);
    }

    private static double roundedToHundredths(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
