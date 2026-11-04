package lt.vsh1ft.groundstation.station;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lt.vsh1ft.groundstation.pass.GroundStation;
import lt.vsh1ft.groundstation.pass.Latitude;
import lt.vsh1ft.groundstation.pass.Longitude;
import lt.vsh1ft.groundstation.pass.MinElevation;

@Schema(description = "A ground station to create or replace")
public record StationRequest(
        @Schema(description = "Unique station name", example = "Vilnius")
        @NotBlank @Size(max = 100) String name,

        @Schema(description = "Geodetic latitude in degrees, north positive", example = "54.67519")
        @NotNull @Latitude Double latitudeDeg,

        @Schema(description = "Geodetic longitude in degrees, east positive", example = "25.23422")
        @NotNull @Longitude Double longitudeDeg,

        @Schema(description = "Height above the WGS84 ellipsoid in metres", example = "114")
        @NotNull Double altitudeM,

        @Schema(description = "Elevation in degrees a satellite must reach before a pass counts; "
                + "slightly negative values allow for stations that see below the geometric horizon",
                example = "5")
        @NotNull @MinElevation Double minElevationDeg) {

    GroundStation toGroundStation() {
        return new GroundStation(name, latitudeDeg, longitudeDeg, altitudeM, minElevationDeg);
    }
}
