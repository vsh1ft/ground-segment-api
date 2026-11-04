package lt.vsh1ft.groundstation.satellite;

import lt.vsh1ft.groundstation.error.ConflictException;
import lt.vsh1ft.groundstation.pass.NoradId;

public class SatelliteAlreadyInCatalogException extends ConflictException {

    public SatelliteAlreadyInCatalogException(NoradId satellite) {
        super("NORAD ID " + satellite + " is already in the catalog");
    }
}
