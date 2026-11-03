package lt.vsh1ft.groundstation.pass;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PassForecastServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
    private static final NoradId ISS = new NoradId(25544);
    private static final GroundStation VILNIUS = new GroundStation("Vilnius", 54.67519, 25.23422, 114.0, 5.0);

    @Test
    void forecastStartsNowAndReturnsWhatThePredictorFound() {
        final var pass = new Pass(NOW.plusSeconds(600), NOW.plusSeconds(1200), new Pass.Peak(NOW.plusSeconds(900), 42.0));
        final var requestedStarts = new ArrayList<Instant>();
        final PassPredictor predictor = (_, _, from, _) -> {
            requestedStarts.add(from);
            return List.of(pass);
        };
        final var service = new PassForecastService(predictor, Clock.fixed(NOW, ZoneOffset.UTC));

        final var passes = service.forecast(ISS, VILNIUS, Duration.ofDays(1));

        assertThat(requestedStarts).containsExactly(NOW);
        assertThat(passes).containsExactly(pass);
    }
}
