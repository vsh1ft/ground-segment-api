package lt.vsh1ft.groundstation.pass;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PassForecastService {

    private final PassPredictor predictor;
    private final Clock clock;

    public List<Pass> forecast(NoradId satellite, GroundStation station, Duration duration) {
        return predictor.predict(satellite, station, clock.instant(), duration);
    }
}
