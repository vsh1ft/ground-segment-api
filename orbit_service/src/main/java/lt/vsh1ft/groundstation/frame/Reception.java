package lt.vsh1ft.groundstation.frame;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lt.vsh1ft.groundstation.station.GroundStation;

public record Reception(ReceptionSource source, UUID stationId, String station, Double latitudeDeg,
                        Double longitudeDeg, Double altitudeM, Long observation, Instant receivedAt) {

    public Reception {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }

    public static Reception satnogs(String station, Double latitudeDeg, Double longitudeDeg, Double altitudeM,
                                    long observation, Instant receivedAt) {
        return new Reception(ReceptionSource.SATNOGS, null, station, latitudeDeg, longitudeDeg, altitudeM, observation,
                receivedAt);
    }

    public static Reception own(UUID stationId, GroundStation station, Instant receivedAt) {
        return new Reception(ReceptionSource.OWN, stationId, station.name(), station.latitudeDeg(),
                station.longitudeDeg(), station.altitudeM(), null, receivedAt);
    }
}
