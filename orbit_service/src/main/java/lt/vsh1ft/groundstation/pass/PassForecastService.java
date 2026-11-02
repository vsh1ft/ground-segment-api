package lt.vsh1ft.groundstation.pass;

import java.time.Clock;
import java.time.Duration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PassForecastService {

    private final PassPredictor predictor;
    private final PassReporter reporter;
    private final Clock clock;

    public void forecast(NoradId satellite, GroundStation station, Duration duration) {
        final var passes = predictor.predict(satellite, station, clock.instant(), duration);
        reporter.report(station, passes);
    }
}
