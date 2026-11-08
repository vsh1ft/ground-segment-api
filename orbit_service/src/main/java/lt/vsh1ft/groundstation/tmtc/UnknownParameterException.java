package lt.vsh1ft.groundstation.tmtc;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.shared.NotFoundException;

public class UnknownParameterException extends NotFoundException {

    public UnknownParameterException(NoradId satellite, String parameter) {
        super("No parameter " + parameter + " in the mission database of satellite " + satellite);
    }
}
