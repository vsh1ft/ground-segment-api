package lt.vsh1ft.groundstation.pass;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface PassPredictor {

    /** Predicts the passes that start and end within {@code [from, from + duration]}. */
    List<Pass> predict(NoradId satellite, GroundStation station, Instant from, Duration duration);
}
