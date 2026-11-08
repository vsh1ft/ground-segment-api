package lt.vsh1ft.groundstation.pass;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lt.vsh1ft.groundstation.shared.NoradId;

@Schema(description = "Predicted passes of one satellite over one station")
public record PassesResponse(
        @Schema(description = "NORAD catalog number of the satellite", example = "25544")
        int satellite,

        @Schema(description = "ID of the station", example = "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42")
        UUID station,

        @Schema(description = "Epoch of the orbital elements the prediction is based on (UTC). Accuracy degrades "
                + "the further the passes are from it, roughly 1-3 km per day for low Earth orbit",
                example = "2026-11-01T18:56:25.712Z")
        Instant elementsEpoch,

        @Schema(description = "Passes in time order")
        List<PassView> passes) {

    private static final double FULL_CIRCLE_DEG = 360.0;

    static PassesResponse of(NoradId satellite, UUID station, PassForecast forecast) {
        return new PassesResponse(satellite.value(), station, truncatedToMillis(forecast.elementsEpoch()),
                forecast.passes().stream().map(PassView::of).toList());
    }

    @Schema(description = "One pass: from rising above the station's minimum elevation to setting below it")
    public record PassView(
            @Schema(description = "When the satellite rises above the minimum elevation (UTC, millisecond precision)",
                    example = "2026-11-02T19:56:46.676Z")
            Instant start,

            @Schema(description = "Where it rises: azimuth in degrees clockwise from true north, rounded to two "
                    + "decimals", example = "338.21")
            double startAzimuthDeg,

            @Schema(description = "Where it rises, on the 16-point compass", example = "NNW")
            CompassPoint startDirection,

            @Schema(description = "Highest point of the pass")
            Peak peak,

            @Schema(description = "When the satellite sets below the minimum elevation (UTC, millisecond precision)",
                    example = "2026-11-02T19:59:38.080Z")
            Instant end,

            @Schema(description = "Where it sets: azimuth in degrees clockwise from true north, rounded to two "
                    + "decimals", example = "102.47")
            double endAzimuthDeg,

            @Schema(description = "Where it sets, on the 16-point compass", example = "ESE")
            CompassPoint endDirection) {

        static PassView of(Pass pass) {
            final var startAzimuth = roundedAzimuth(pass.start().azimuthDeg());
            final var peakAzimuth = roundedAzimuth(pass.peak().azimuthDeg());
            final var endAzimuth = roundedAzimuth(pass.end().azimuthDeg());
            return new PassView(
                    truncatedToMillis(pass.start().time()), startAzimuth, CompassPoint.of(startAzimuth),
                    new Peak(truncatedToMillis(pass.peak().time()), roundedToHundredths(pass.peak().elevationDeg()),
                            peakAzimuth, CompassPoint.of(peakAzimuth)),
                    truncatedToMillis(pass.end().time()), endAzimuth, CompassPoint.of(endAzimuth));
        }
    }

    @Schema(description = "Highest point of a pass")
    public record Peak(
            @Schema(description = "Time of the highest elevation (UTC, millisecond precision)",
                    example = "2026-11-02T19:58:12.161Z")
            Instant time,

            @Schema(description = "Highest elevation above the horizon in degrees, rounded to two decimals",
                    example = "6.2")
            double elevationDeg,

            @Schema(description = "Azimuth at the highest point in degrees clockwise from true north, rounded to two "
                    + "decimals", example = "40.12")
            double azimuthDeg,

            @Schema(description = "Direction of the highest point on the 16-point compass", example = "NE")
            CompassPoint direction) {
    }

    private static Instant truncatedToMillis(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MILLIS);
    }

    private static double roundedToHundredths(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static double roundedAzimuth(double azimuthDeg) {
        return roundedToHundredths(azimuthDeg) % FULL_CIRCLE_DEG;
    }
}
