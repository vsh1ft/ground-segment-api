package lt.vsh1ft.groundstation.orekit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.time.Duration;
import java.time.Instant;

import lt.vsh1ft.groundstation.pass.GroundStation;
import lt.vsh1ft.groundstation.pass.NoradId;
import lt.vsh1ft.groundstation.pass.Pass;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.analytical.tle.TLE;

class OrekitPassPredictorIntegrationTest {

    private static final NoradId ISS = new NoradId(25544);
    private static final String ISS_LINE_1 =
            "1 25544U 98067A   26304.17644455  .00006183  00000-0  11976-3 0  9996";
    private static final String ISS_LINE_2 =
            "2 25544  51.6332 347.1486 0007016 330.4977  29.5614 15.49124187592838";
    private static final Instant EPOCH = Instant.parse("2026-10-31T04:14:04Z");
    private static final GroundStation VILNIUS = new GroundStation("Vilnius", 54.67519, 25.23422, 114.0);
    private static final double MIN_ELEVATION_DEG = 5.0;

    private static OrekitPassPredictor predictor;

    @BeforeAll
    static void setUp() {
        new OrekitDataLoader(new File("orekit-data"));
        final var issTle = new TLE(ISS_LINE_1, ISS_LINE_2);
        predictor = new OrekitPassPredictor(
                _ -> issTle,
                new PassDetectionProperties(MIN_ELEVATION_DEG, 30.0, 1.0e-3));
    }

    @Test
    void issPassesOverVilniusArePhysicallyPlausible() {
        final var passes = predictor.predict(ISS, VILNIUS, EPOCH, Duration.ofDays(1));

        assertThat(passes).hasSizeBetween(3, 10);
        for (Pass pass : passes) {
            assertThat(Duration.between(pass.start(), pass.end())).isLessThan(Duration.ofMinutes(12));
            assertThat(pass.peak().elevationDeg()).isBetween(MIN_ELEVATION_DEG, 90.0);
            assertThat(pass.start()).isAfterOrEqualTo(EPOCH);
            assertThat(pass.end()).isBeforeOrEqualTo(EPOCH.plus(Duration.ofDays(1)));
        }
        for (int i = 1; i < passes.size(); i++) {
            assertThat(Duration.between(passes.get(i - 1).start(), passes.get(i).start()))
                    .isGreaterThan(Duration.ofMinutes(80));
        }
    }

    @Test
    void repeatedPredictionsGiveTheSameResult() {
        final var first = predictor.predict(ISS, VILNIUS, EPOCH, Duration.ofDays(1));
        final var second = predictor.predict(ISS, VILNIUS, EPOCH, Duration.ofDays(1));

        assertThat(second).isEqualTo(first);
    }

    @Test
    void laterWindowOnlyContainsPassesInsideIt() {
        final var from = EPOCH.plus(Duration.ofHours(12));
        final var passes = predictor.predict(ISS, VILNIUS, from, Duration.ofHours(6));

        assertThat(passes).allSatisfy(pass -> assertThat(pass.start()).isAfterOrEqualTo(from));
    }
}
