package lt.vsh1ft.groundstation.satellite;

import lt.vsh1ft.groundstation.error.NotFoundException;
import lt.vsh1ft.groundstation.pass.NoradId;

public class SatelliteNotInCatalogException extends NotFoundException {

    public SatelliteNotInCatalogException(NoradId satellite) {
        super("No satellite with NORAD ID " + satellite + " in the catalog");
    }
}
