package lt.vsh1ft.groundstation.orekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
    private static final double MIN_ELEVATION_DEG = 5.0;
    private static final GroundStation VILNIUS =
            new GroundStation("Vilnius", 54.67519, 25.23422, 114.0, MIN_ELEVATION_DEG);
    private static final List<Pass> SKYFIELD_PASSES = List.of(
            skyfieldPass("2026-10-31T04:44:22.190429Z", "2026-10-31T04:45:10.409439Z", 5.367992, "2026-10-31T04:45:58.606281Z"),
            skyfieldPass("2026-10-31T21:29:38.207428Z", "2026-10-31T21:32:49.301423Z", 13.930483, "2026-10-31T21:36:01.520215Z"),
            skyfieldPass("2026-10-31T23:04:53.190713Z", "2026-10-31T23:08:58.693907Z", 36.931867, "2026-10-31T23:13:05.781483Z"),
            skyfieldPass("2026-11-01T00:41:16.701447Z", "2026-11-01T00:45:29.585370Z", 50.069797, "2026-11-01T00:49:43.214531Z"),
            skyfieldPass("2026-11-01T02:17:57.820575Z", "2026-11-01T02:21:56.223023Z", 28.489011, "2026-11-01T02:25:54.442491Z"),
            skyfieldPass("2026-11-01T03:55:32.841925Z", "2026-11-01T03:57:54.865415Z", 8.811268, "2026-11-01T04:00:16.696551Z"));

    private static OrekitPassPredictor predictor;

    @BeforeAll
    static void setUp() {
        new OrekitDataLoader(new File("orekit-data"));
        final var issTle = new TLE(ISS_LINE_1, ISS_LINE_2);
        predictor = new OrekitPassPredictor(
                _ -> issTle,
                new PassDetectionProperties(30.0, 1.0e-3));
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
    void issPassesOverVilniusMatchSkyfieldWithinOneSecond() {
        final var passes = predictor.predict(ISS, VILNIUS, EPOCH, Duration.ofDays(1));

        assertThat(passes).hasSameSizeAs(SKYFIELD_PASSES);
        for (int i = 0; i < passes.size(); i++) {
            final var ours = passes.get(i);
            final var reference = SKYFIELD_PASSES.get(i);
            assertThat(ours.start()).isCloseTo(reference.start(), within(1, ChronoUnit.SECONDS));
            assertThat(ours.peak().time()).isCloseTo(reference.peak().time(), within(1, ChronoUnit.SECONDS));
            assertThat(ours.end()).isCloseTo(reference.end(), within(1, ChronoUnit.SECONDS));
            assertThat(ours.peak().elevationDeg()).isCloseTo(reference.peak().elevationDeg(), within(0.01));
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

    private static Pass skyfieldPass(String start, String peak, double peakElevationDeg, String end) {
        return new Pass(Instant.parse(start), Instant.parse(end), new Pass.Peak(Instant.parse(peak), peakElevationDeg));
    }
}
