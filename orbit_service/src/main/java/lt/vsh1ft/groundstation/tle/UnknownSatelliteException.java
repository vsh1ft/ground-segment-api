package lt.vsh1ft.groundstation.tle;

import lt.vsh1ft.groundstation.pass.NoradId;

public class UnknownSatelliteException extends CelestrakException {

    public UnknownSatelliteException(NoradId satellite) {
        super("CelesTrak has no orbit data for NORAD ID " + satellite);
    }
}
