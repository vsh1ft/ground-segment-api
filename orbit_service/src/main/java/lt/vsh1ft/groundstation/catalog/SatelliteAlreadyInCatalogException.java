package lt.vsh1ft.groundstation.catalog;

import lt.vsh1ft.groundstation.shared.ConflictException;
import lt.vsh1ft.groundstation.shared.NoradId;

public class SatelliteAlreadyInCatalogException extends ConflictException {

    public SatelliteAlreadyInCatalogException(NoradId satellite) {
        super("NORAD ID " + satellite + " is already in the catalog");
    }
}
