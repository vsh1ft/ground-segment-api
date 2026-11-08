package lt.vsh1ft.groundstation.alarm;

import lt.vsh1ft.groundstation.shared.ConflictException;

public class AlarmChangedException extends ConflictException {

    public AlarmChangedException(long id) {
        super("Alarm " + id + " changed while it was being acknowledged; read it again and retry");
    }
}
