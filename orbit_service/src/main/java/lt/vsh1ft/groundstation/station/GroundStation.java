package lt.vsh1ft.groundstation.station;

public record GroundStation(
        String name,
        @Latitude double latitudeDeg,
        @Longitude double longitudeDeg,
        double altitudeM,
        @MinElevation double minElevationDeg) {
}
