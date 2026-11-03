package lt.vsh1ft.groundstation.station;

import java.util.UUID;

import lt.vsh1ft.groundstation.pass.GroundStation;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("station")
public record Station(
        @Id UUID id,
        String name,
        double latitudeDeg,
        double longitudeDeg,
        double altitudeM,
        double minElevationDeg) {

    static Station of(UUID id, GroundStation station) {
        return new Station(id, station.name(), station.latitudeDeg(), station.longitudeDeg(), station.altitudeM(),
                station.minElevationDeg());
    }

    public GroundStation toGroundStation() {
        return new GroundStation(name, latitudeDeg, longitudeDeg, altitudeM, minElevationDeg);
    }
}
