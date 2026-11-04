package lt.vsh1ft.groundstation.elements;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.shared.NotFoundException;

public class SatelliteNotFoundException extends NotFoundException {

    public SatelliteNotFoundException(NoradId satellite) {
        super("No orbit data for NORAD ID " + satellite);
    }

    public SatelliteNotFoundException(NoradId satellite, Throwable cause) {
        super("No orbit data for NORAD ID " + satellite, cause);
    }
}
