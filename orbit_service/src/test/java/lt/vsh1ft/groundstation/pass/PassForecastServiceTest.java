package lt.vsh1ft.groundstation.pass;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PassForecastServiceTest {

    private static final NoradId ISS = new NoradId(25544);
    private static final GroundStation VILNIUS = new GroundStation("Vilnius", 54.67519, 25.23422, 114.0);

    @Test
    void forecastStartsNowAndReportsWhatThePredictorFound() {
        final var before = Instant.now();
        final var pass = new Pass(before.plusSeconds(600), before.plusSeconds(1200),
                new Pass.Peak(before.plusSeconds(900), 42.0));
        final var requestedStarts = new ArrayList<Instant>();
        final var reported = new ArrayList<List<Pass>>();

        final PassPredictor predictor = (satellite, station, from, duration) -> {
            requestedStarts.add(from);
            return List.of(pass);
        };
        final PassReporter reporter = (station, passes) -> reported.add(passes);
        final var service = new PassForecastService(predictor, reporter);

        service.forecast(ISS, VILNIUS, Duration.ofDays(1));
        final var after = Instant.now();

        assertThat(requestedStarts).singleElement().satisfies(from -> assertThat(from).isBetween(before, after));
        assertThat(reported).containsExactly(List.of(pass));
    }
}
