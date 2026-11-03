package lt.vsh1ft.groundstation.pass;

public record GroundStation(String name, double latitudeDeg, double longitudeDeg, double altitudeM,
                            double minElevationDeg) {

    public GroundStation {
        if (latitudeDeg < -90 || latitudeDeg > 90) {
            throw new IllegalArgumentException("Latitude out of range: " + latitudeDeg);
        }
        if (longitudeDeg < -180 || longitudeDeg > 180) {
            throw new IllegalArgumentException("Longitude out of range: " + longitudeDeg);
        }
        if (minElevationDeg < -5 || minElevationDeg > 90) {
            throw new IllegalArgumentException("Minimum elevation out of range: " + minElevationDeg);
        }
    }
}
