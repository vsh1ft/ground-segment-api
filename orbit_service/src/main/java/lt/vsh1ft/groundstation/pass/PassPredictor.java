package lt.vsh1ft.groundstation.pass;

import java.time.Duration;
import java.time.Instant;

import lt.vsh1ft.groundstation.shared.NoradId;

public interface PassPredictor {

    PassForecast predict(NoradId satellite, GroundStation station, Instant from, Duration duration);
}
