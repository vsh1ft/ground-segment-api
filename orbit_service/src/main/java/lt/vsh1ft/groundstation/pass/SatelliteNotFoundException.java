package lt.vsh1ft.groundstation.pass;

import lt.vsh1ft.groundstation.error.NotFoundException;

public class SatelliteNotFoundException extends NotFoundException {

    public SatelliteNotFoundException(NoradId satellite) {
        super("No orbit data for NORAD ID " + satellite);
    }

    public SatelliteNotFoundException(NoradId satellite, Throwable cause) {
        super("No orbit data for NORAD ID " + satellite, cause);
    }
}
