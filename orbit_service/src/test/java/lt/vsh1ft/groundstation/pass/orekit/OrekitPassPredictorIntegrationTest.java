package lt.vsh1ft.groundstation.pass.orekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import lt.vsh1ft.groundstation.station.GroundStation;
import lt.vsh1ft.groundstation.pass.Pass;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.shared.OrekitDataLoader;
import lt.vsh1ft.groundstation.traceability.Requirement;
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
            skyfieldPass("2026-10-31T04:44:22.190429Z", 236.9751, "2026-10-31T04:45:10.409439Z", 5.367992, 226.0853,
                    "2026-10-31T04:45:58.606281Z", 215.1884),
            skyfieldPass("2026-10-31T21:29:38.207428Z", 192.8728, "2026-10-31T21:32:49.301423Z", 13.930483, 144.1769,
                    "2026-10-31T21:36:01.520215Z", 95.6189),
            skyfieldPass("2026-10-31T23:04:53.190713Z", 236.4353, "2026-10-31T23:08:58.693907Z", 36.931867, 163.3021,
                    "2026-10-31T23:13:05.781483Z", 90.2598),
            skyfieldPass("2026-11-01T00:41:16.701447Z", 262.0688, "2026-11-01T00:45:29.585370Z", 50.069797, 183.2583,
                    "2026-11-01T00:49:43.214531Z", 104.4593),
            skyfieldPass("2026-11-01T02:17:57.820575Z", 270.4854, "2026-11-01T02:21:56.223023Z", 28.489011, 202.9964,
                    "2026-11-01T02:25:54.442491Z", 135.4562),
            skyfieldPass("2026-11-01T03:55:32.841925Z", 255.2861, "2026-11-01T03:57:54.865415Z", 8.811268, 221.6320,
                    "2026-11-01T04:00:16.696551Z", 187.9265));

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
    @Requirement("PERF-010")
    void sevenDayPredictionTakesLessThanTwoSeconds() {
        predictor.predict(ISS, VILNIUS, EPOCH, Duration.ofHours(1));
        final var start = System.nanoTime();

        final var passes = predictor.predict(ISS, VILNIUS, EPOCH, Duration.ofDays(7)).passes();

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        assertThat(passes).isNotEmpty();
    }

    @Test
    @Requirement("PASS-060")
    void issPassesOverVilniusArePhysicallyPlausible() {
        final var passes = predictor.predict(ISS, VILNIUS, EPOCH, Duration.ofDays(1)).passes();

        assertThat(passes).hasSizeBetween(3, 10);
        for (Pass pass : passes) {
            assertThat(Duration.between(pass.start().time(), pass.end().time())).isLessThan(Duration.ofMinutes(12));
            assertThat(pass.peak().elevationDeg()).isBetween(MIN_ELEVATION_DEG, 90.0);
            assertThat(pass.start().time()).isAfterOrEqualTo(EPOCH);
            assertThat(pass.end().time()).isBeforeOrEqualTo(EPOCH.plus(Duration.ofDays(1)));
        }
        for (int i = 1; i < passes.size(); i++) {
            assertThat(Duration.between(passes.get(i - 1).start().time(), passes.get(i).start().time()))
                    .isGreaterThan(Duration.ofMinutes(80));
        }
    }

    @Test
    @Requirement({"PASS-020", "PASS-060"})
    void issPassesOverVilniusMatchSkyfieldWithinOneSecond() {
        final var passes = predictor.predict(ISS, VILNIUS, EPOCH, Duration.ofDays(1)).passes();

        assertThat(passes).hasSameSizeAs(SKYFIELD_PASSES);
        for (int i = 0; i < passes.size(); i++) {
            final var ours = passes.get(i);
            final var reference = SKYFIELD_PASSES.get(i);
            assertThat(ours.start().time()).isCloseTo(reference.start().time(), within(1, ChronoUnit.SECONDS));
            assertThat(ours.peak().time()).isCloseTo(reference.peak().time(), within(1, ChronoUnit.SECONDS));
            assertThat(ours.end().time()).isCloseTo(reference.end().time(), within(1, ChronoUnit.SECONDS));
            assertThat(ours.peak().elevationDeg()).isCloseTo(reference.peak().elevationDeg(), within(0.01));
        }
    }

    @Test
    @Requirement("PASS-080")
    void issRisePeakAndSetAzimuthsOverVilniusMatchSkyfieldWithinATenthOfADegree() {
        final var passes = predictor.predict(ISS, VILNIUS, EPOCH, Duration.ofDays(1)).passes();

        assertThat(passes).hasSameSizeAs(SKYFIELD_PASSES);
        for (int i = 0; i < passes.size(); i++) {
            final var ours = passes.get(i);
            final var reference = SKYFIELD_PASSES.get(i);
            assertThat(ours.start().azimuthDeg()).isCloseTo(reference.start().azimuthDeg(), within(0.1));
            assertThat(ours.peak().azimuthDeg()).isCloseTo(reference.peak().azimuthDeg(), within(0.1));
            assertThat(ours.end().azimuthDeg()).isCloseTo(reference.end().azimuthDeg(), within(0.1));
        }
    }

    @Test
    @Requirement("PASS-010")
    void forecastCarriesTheEpochOfTheElementsItUsed() {
        final var forecast = predictor.predict(ISS, VILNIUS, EPOCH, Duration.ofHours(1));

        assertThat(forecast.elementsEpoch()).isCloseTo(EPOCH, within(1, ChronoUnit.SECONDS));
    }

    @Test
    @Requirement("PASS-070")
    void repeatedPredictionsGiveTheSameResult() {
        final var first = predictor.predict(ISS, VILNIUS, EPOCH, Duration.ofDays(1));
        final var second = predictor.predict(ISS, VILNIUS, EPOCH, Duration.ofDays(1));

        assertThat(second).isEqualTo(first);
    }

    @Test
    @Requirement("PASS-070")
    void laterWindowOnlyContainsPassesInsideIt() {
        final var from = EPOCH.plus(Duration.ofHours(12));
        final var passes = predictor.predict(ISS, VILNIUS, from, Duration.ofHours(6)).passes();

        assertThat(passes).allSatisfy(pass -> assertThat(pass.start().time()).isAfterOrEqualTo(from));
    }

    private static Pass skyfieldPass(String start, double startAzimuthDeg, String peak, double peakElevationDeg,
                                     double peakAzimuthDeg, String end, double endAzimuthDeg) {
        return new Pass(new Pass.Crossing(Instant.parse(start), startAzimuthDeg),
                new Pass.Crossing(Instant.parse(end), endAzimuthDeg),
                new Pass.Peak(Instant.parse(peak), peakElevationDeg, peakAzimuthDeg));
    }
}
