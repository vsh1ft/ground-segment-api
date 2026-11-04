package lt.vsh1ft.groundstation.catalog;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.shared.NotFoundException;

public class SatelliteNotInCatalogException extends NotFoundException {

    public SatelliteNotInCatalogException(NoradId satellite) {
        super("No satellite with NORAD ID " + satellite + " in the catalog");
    }
}
