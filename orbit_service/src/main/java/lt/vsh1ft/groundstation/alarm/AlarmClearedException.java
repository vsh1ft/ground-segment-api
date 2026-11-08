package lt.vsh1ft.groundstation.alarm;

import lt.vsh1ft.groundstation.shared.ConflictException;

public class AlarmClearedException extends ConflictException {

    public AlarmClearedException(long id) {
        super("Alarm " + id + " is already cleared");
    }
}
