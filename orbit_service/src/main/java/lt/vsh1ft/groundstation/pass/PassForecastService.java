package lt.vsh1ft.groundstation.pass;

import java.time.Clock;
import java.time.Duration;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.station.GroundStation;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PassForecastService {

    private final PassPredictor predictor;
    private final Clock clock;

    public PassForecast forecast(NoradId satellite, GroundStation station, Duration duration) {
        return predictor.predict(satellite, station, clock.instant(), duration);
    }
}
