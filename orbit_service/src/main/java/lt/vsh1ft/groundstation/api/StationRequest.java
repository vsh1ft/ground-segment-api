package lt.vsh1ft.groundstation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lt.vsh1ft.groundstation.pass.GroundStation;
import lt.vsh1ft.groundstation.pass.Latitude;
import lt.vsh1ft.groundstation.pass.Longitude;
import lt.vsh1ft.groundstation.pass.MinElevation;

public record StationRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Latitude Double latitudeDeg,
        @NotNull @Longitude Double longitudeDeg,
        @NotNull Double altitudeM,
        @NotNull @MinElevation Double minElevationDeg) {

    GroundStation toGroundStation() {
        return new GroundStation(name, latitudeDeg, longitudeDeg, altitudeM, minElevationDeg);
    }
}
