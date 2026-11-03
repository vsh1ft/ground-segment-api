package lt.vsh1ft.groundstation.pass;

public class SatelliteNotFoundException extends RuntimeException {

    public SatelliteNotFoundException(NoradId satellite, Throwable cause) {
        super("No orbit data for NORAD ID " + satellite, cause);
    }
}
