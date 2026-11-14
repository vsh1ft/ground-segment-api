package lt.vsh1ft.groundstation.tracking;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Where the satellite is, seen from the station, at one moment")
public record TrackPoint(
        @Schema(description = "UTC", example = "2026-11-01T00:41:17Z")
        Instant time,

        @Schema(description = "Compass direction, degrees: 0 north, 90 east, at least 0 and below 360",
                example = "262.06")
        double azimuthDeg,

        @Schema(description = "Geometric elevation above the horizon, degrees; negative below it", example = "5.02")
        double elevationDeg,

        @Schema(description = "Distance from the station to the satellite, km", example = "1876.78")
        double rangeKm,

        @Schema(description = "How fast the distance changes, km/s: negative while approaching", example = "-6.762")
        double rangeRateKmPerS,

        @Schema(description = "Doppler-corrected value of each entry of `frequencies`, in the same order, Hz",
                example = "[145828289, 145821711]")
        List<Long> frequenciesHz) {

    public TrackPoint {
        frequenciesHz = List.copyOf(frequenciesHz);
    }

    static TrackPoint of(OrekitTracker.Geometry geometry, List<Frequency> frequencies) {
        return new TrackPoint(geometry.time(), geometry.azimuthDeg(), geometry.elevationDeg(), geometry.rangeKm(),
                geometry.rangeRateKmPerS(),
                frequencies.stream().map(frequency -> frequency.correctedHz(geometry.rangeRateKmPerS())).toList());
    }
}
