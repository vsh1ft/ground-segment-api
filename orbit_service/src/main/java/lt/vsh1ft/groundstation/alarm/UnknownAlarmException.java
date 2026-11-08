package lt.vsh1ft.groundstation.alarm;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.shared.NotFoundException;

public class UnknownAlarmException extends NotFoundException {

    public UnknownAlarmException(NoradId satellite, long id) {
        super("No alarm " + id + " for satellite " + satellite);
    }
}
