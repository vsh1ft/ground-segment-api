package lt.vsh1ft.groundstation.pass;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import lt.vsh1ft.groundstation.shared.NoradId;

public interface PassPredictor {

    List<Pass> predict(NoradId satellite, GroundStation station, Instant from, Duration duration);
}
