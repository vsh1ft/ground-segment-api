package lt.vsh1ft.groundstation.station;

import java.util.UUID;

import lt.vsh1ft.groundstation.error.NotFoundException;

public class StationNotFoundException extends NotFoundException {

    public StationNotFoundException(UUID id) {
        super("No station with ID " + id);
    }
}
