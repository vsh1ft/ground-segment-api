package lt.vsh1ft.groundstation.station;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("station")
@Schema(description = "A ground station")
public record Station(
        @Schema(description = "Station ID", example = "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42")
        @Id UUID id,

        @Schema(description = "Unique station name", example = "Vilnius")
        String name,

        @Schema(description = "Geodetic latitude in degrees, north positive", example = "54.67519")
        double latitudeDeg,

        @Schema(description = "Geodetic longitude in degrees, east positive", example = "25.23422")
        double longitudeDeg,

        @Schema(description = "Height above the WGS84 ellipsoid in metres", example = "114.0")
        double altitudeM,

        @Schema(description = "Elevation in degrees a satellite must reach before a pass counts", example = "5.0")
        double minElevationDeg) {

    static Station of(UUID id, GroundStation station) {
        return new Station(id, station.name(), station.latitudeDeg(), station.longitudeDeg(), station.altitudeM(),
                station.minElevationDeg());
    }

    public GroundStation toGroundStation() {
        return new GroundStation(name, latitudeDeg, longitudeDeg, altitudeM, minElevationDeg);
    }
}
