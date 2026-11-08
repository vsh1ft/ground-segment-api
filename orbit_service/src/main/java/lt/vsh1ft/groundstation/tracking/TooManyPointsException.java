package lt.vsh1ft.groundstation.tracking;

import java.time.Duration;

import lt.vsh1ft.groundstation.shared.BadRequestException;

public class TooManyPointsException extends BadRequestException {

    public TooManyPointsException(Duration duration, Duration step, long points, long max) {
        super("A track of " + duration + " at a step of " + step + " has " + points + " points; at most " + max
                + " are allowed, so ask for a shorter track or a longer step");
    }
}
