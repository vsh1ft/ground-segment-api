package lt.vsh1ft.groundstation.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.File;
import java.time.Duration;
import java.time.Instant;

import lt.vsh1ft.groundstation.pass.GroundStation;
import lt.vsh1ft.groundstation.shared.OrekitDataLoader;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.orekit.propagation.analytical.tle.TLE;

class OrekitTrackerTest {

    private static final GroundStation VILNIUS = new GroundStation("Vilnius", 54.67519, 25.23422, 114.0, 5.0);

    private static TLE iss;

    private final OrekitTracker tracker = new OrekitTracker();

    @BeforeAll
    static void loadOrekitData() {
        new OrekitDataLoader(new File("orekit-data"));
        iss = new TLE("1 25544U 98067A   26304.17644455  .00006183  00000-0  11976-3 0  9996",
                "2 25544  51.6332 347.1486 0007016 330.4977  29.5614 15.49124187592838");
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-01T00:39:00Z, 265.455952, -3.673434, 2811.549990, -6.843388131",
            "2026-11-01T00:41:17Z, 262.057142, 5.023694, 1876.779526, -6.761668368",
            "2026-11-01T00:43:30Z, 251.329058, 20.902908, 1011.759645, -5.993967476",
            "2026-11-01T00:45:30Z, 182.759572, 50.068681, 545.694854, 0.043527516",
            "2026-11-01T00:47:30Z, 115.094322, 20.790679, 1017.303137, 6.006122130",
            "2026-11-01T00:49:43Z, 104.467599, 5.016915, 1882.795151, 6.760857233"})
    @Requirement({"TRK-010", "TRK-030"})
    void issOverVilniusAgreesWithSkyfield(Instant time, double azimuthDeg, double elevationDeg, double rangeKm,
                                          double rangeRateKmPerS) {
        final var point = tracker.track(iss, VILNIUS, time, Duration.ofSeconds(1), 1).getFirst();

        assertThat(point.time()).isEqualTo(time);
        assertThat(point.azimuthDeg()).isCloseTo(azimuthDeg, within(0.01));
        assertThat(point.elevationDeg()).isCloseTo(elevationDeg, within(0.01));
        assertThat(point.rangeKm()).isCloseTo(rangeKm, within(0.1));
        assertThat(point.rangeRateKmPerS()).isCloseTo(rangeRateKmPerS, within(0.001));
    }

    @ParameterizedTest
    @CsvSource({"PT1S, 541", "PT0.1S, 5401", "PT10S, 55"})
    @Requirement("TRK-010")
    void pointsComeOneStepApartFromTheStart(Duration step, long points) {
        final var from = Instant.parse("2026-11-01T00:41:17Z");

        final var track = tracker.track(iss, VILNIUS, from, step, points);

        assertThat(track).hasSize((int) points);
        assertThat(track.get(1).time()).isEqualTo(from.plus(step));
        assertThat(track.getLast().time()).isEqualTo(from.plus(step.multipliedBy(points - 1)));
        assertThat(track).allSatisfy(point -> assertThat(point.azimuthDeg()).isGreaterThanOrEqualTo(0).isLessThan(360));
    }
}
