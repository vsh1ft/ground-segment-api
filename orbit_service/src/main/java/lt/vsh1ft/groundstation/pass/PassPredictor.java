package lt.vsh1ft.groundstation.pass;

import java.time.Duration;
import java.time.Instant;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.station.GroundStation;

public interface PassPredictor {

    PassForecast predict(NoradId satellite, GroundStation station, Instant from, Duration duration);
}
