package lt.vsh1ft.groundstation.pass;

import java.time.Duration;
import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PassForecastService {

    private final PassPredictor predictor;
    private final PassReporter reporter;

    public void forecast(NoradId satellite, GroundStation station, Duration duration) {
        final var passes = predictor.predict(satellite, station, Instant.now(), duration);
        reporter.report(station, passes);
    }
}
