package lt.vsh1ft.simulator;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

record GroundContact(Path tleFile, double latitudeDeg, double longitudeDeg, double altitudeM,
                     double minElevationDeg, Path orekitData) {

    static final String TLE_FILE = "SIMULATOR_TLE_FILE";
    static final String LATITUDE = "SIMULATOR_STATION_LATITUDE";
    static final String LONGITUDE = "SIMULATOR_STATION_LONGITUDE";
    static final String ALTITUDE = "SIMULATOR_STATION_ALTITUDE";
    static final String MIN_ELEVATION = "SIMULATOR_STATION_MIN_ELEVATION";
    static final String OREKIT_DATA = "SIMULATOR_OREKIT_DATA";
    private static final String DEFAULT_OREKIT_DATA = "orekit-data";

    GroundContact {
        requireRange(LATITUDE, latitudeDeg, -90, 90);
        requireRange(LONGITUDE, longitudeDeg, -180, 180);
        requireRange(MIN_ELEVATION, minElevationDeg, -5, 90);
    }

    static Optional<GroundContact> from(Map<String, String> environment) {
        return Optional.ofNullable(environment.get(TLE_FILE)).map(tleFile -> new GroundContact(
                Path.of(tleFile),
                required(environment, LATITUDE),
                required(environment, LONGITUDE),
                required(environment, ALTITUDE),
                required(environment, MIN_ELEVATION),
                Path.of(environment.getOrDefault(OREKIT_DATA, DEFAULT_OREKIT_DATA))));
    }

    private static double required(Map<String, String> environment, String name) {
        final var value = environment.get(name);
        if (value == null) {
            throw new IllegalArgumentException(name + " is required when " + TLE_FILE + " is set");
        }
        return Double.parseDouble(value);
    }

    private static void requireRange(String name, double value, double min, double max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ", was " + value);
        }
    }
}
