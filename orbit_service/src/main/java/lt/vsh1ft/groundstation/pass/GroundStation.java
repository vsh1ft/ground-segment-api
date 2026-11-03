package lt.vsh1ft.groundstation.pass;

public record GroundStation(
        String name,
        @Latitude double latitudeDeg,
        @Longitude double longitudeDeg,
        double altitudeM,
        @MinElevation double minElevationDeg) {
}
