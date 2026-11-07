package lt.vsh1ft.groundstation.catalog;

import java.time.Instant;
import java.time.ZoneOffset;

import io.swagger.v3.oas.annotations.media.Schema;
import lt.vsh1ft.groundstation.elements.StoredOmm;

@Schema(description = "One set of mean orbital elements (CCSDS OMM, for SGP4)")
public record ElementSet(
        @Schema(description = "Time the elements are valid for, UTC", example = "2026-11-01T18:56:25.712736Z")
        Instant epoch,

        @Schema(description = "Mean motion, revolutions per day", example = "15.49143202")
        double meanMotion,

        @Schema(description = "Eccentricity", example = "0.0007095")
        double eccentricity,

        @Schema(description = "Inclination, degrees", example = "51.6332")
        double inclination,

        @Schema(description = "Right ascension of the ascending node, degrees", example = "339.1679")
        double raOfAscNode,

        @Schema(description = "Argument of pericenter, degrees", example = "336.8847")
        double argOfPericenter,

        @Schema(description = "Mean anomaly, degrees", example = "23.1822")
        double meanAnomaly,

        @Schema(description = "SGP4 drag term B*, 1/Earth radii", example = "0.00011503")
        double bstar,

        @Schema(description = "First derivative of mean motion divided by 2, revolutions per day squared",
                example = "0.00005925")
        double meanMotionDot,

        @Schema(description = "Second derivative of mean motion divided by 6, revolutions per day cubed",
                example = "0")
        double meanMotionDdot,

        @Schema(description = "Element set number assigned by the publisher", example = "999")
        int elementSetNo,

        @Schema(description = "Revolution number at epoch", example = "59308")
        int revAtEpoch,

        @Schema(description = "When these elements were last downloaded from CelesTrak, UTC",
                example = "2026-11-04T10:00:00Z")
        Instant downloadedAt) {

    static ElementSet from(StoredOmm omm) {
        return new ElementSet(omm.epoch().toInstant(ZoneOffset.UTC), omm.meanMotion(), omm.eccentricity(),
                omm.inclination(), omm.raOfAscNode(), omm.argOfPericenter(), omm.meanAnomaly(), omm.bstar(),
                omm.meanMotionDot(), omm.meanMotionDdot(), omm.elementSetNo(), omm.revAtEpoch(), omm.downloadedAt());
    }
}
