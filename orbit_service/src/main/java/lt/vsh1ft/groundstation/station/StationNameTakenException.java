package lt.vsh1ft.groundstation.station;

import lt.vsh1ft.groundstation.error.ConflictException;

public class StationNameTakenException extends ConflictException {

    public StationNameTakenException(String name) {
        super("A station named '" + name + "' already exists");
    }
}
