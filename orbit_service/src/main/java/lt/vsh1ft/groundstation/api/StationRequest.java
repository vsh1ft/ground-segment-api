package lt.vsh1ft.groundstation.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lt.vsh1ft.groundstation.pass.GroundStation;

public record StationRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitudeDeg,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitudeDeg,
        @NotNull Double altitudeM,
        @NotNull @DecimalMin("-5") @DecimalMax("90") Double minElevationDeg) {

    GroundStation toGroundStation() {
        return new GroundStation(name, latitudeDeg, longitudeDeg, altitudeM, minElevationDeg);
    }
}
