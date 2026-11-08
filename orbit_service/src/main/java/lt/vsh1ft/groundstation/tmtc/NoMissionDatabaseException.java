package lt.vsh1ft.groundstation.tmtc;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.shared.NotFoundException;

public class NoMissionDatabaseException extends NotFoundException {

    public NoMissionDatabaseException(NoradId satellite) {
        super("No mission database for satellite " + satellite);
    }
}
