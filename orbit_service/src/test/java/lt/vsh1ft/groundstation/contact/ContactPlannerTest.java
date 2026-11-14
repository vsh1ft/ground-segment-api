package lt.vsh1ft.groundstation.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import lt.vsh1ft.groundstation.elements.OrbitDataUnavailableException;
import lt.vsh1ft.groundstation.elements.TleSource;
import lt.vsh1ft.groundstation.station.GroundStation;
import lt.vsh1ft.groundstation.station.GroundStationSource;
import lt.vsh1ft.groundstation.pass.orekit.OrekitPassPredictor;
import lt.vsh1ft.groundstation.pass.orekit.PassDetectionProperties;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.shared.OrekitDataLoader;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.analytical.tle.TLE;

class ContactPlannerTest {

    private static final NoradId ISS = new NoradId(25544);
    private static final GroundStation VILNIUS = new GroundStation("Vilnius", 54.67519, 25.23422, 114.0, 5.0);
    private static final Instant PASS_START = Instant.parse("2026-10-31T23:04:53.190713Z");
    private static final Instant PASS_END = Instant.parse("2026-10-31T23:13:05.781483Z");
    private static final Instant MID_PASS = Instant.parse("2026-10-31T23:09:00Z");

    private static TLE issTle;

    private final GroundStationSource stations = mock(GroundStationSource.class);
    private final TleSource tles = mock(TleSource.class);
    private final ContactPlanner planner = new ContactPlanner(stations,
            new OrekitPassPredictor(tles, new PassDetectionProperties(30.0, 1.0e-3)));

    @BeforeAll
    static void loadOrekitData() {
        new OrekitDataLoader(new File("orekit-data"));
        issTle = new TLE("1 25544U 98067A   26304.17644455  .00006183  00000-0  11976-3 0  9996",
                "2 25544  51.6332 347.1486 0007016 330.4977  29.5614 15.49124187592838");
    }

    @Test
    @Requirement("CON-010")
    void planMadeDuringAPassContainsThatPassAndTheNextDay() {
        given(stations.byName("Vilnius")).willReturn(Optional.of(VILNIUS));
        given(tles.latest(ISS)).willReturn(issTle);

        final var plan = planner.plan(ISS, "Vilnius", MID_PASS);

        final var current = plan.passAt(MID_PASS).orElseThrow();
        assertThat(Duration.between(current.start().time(), PASS_START).abs()).isLessThan(Duration.ofSeconds(1));
        assertThat(Duration.between(current.end().time(), PASS_END).abs()).isLessThan(Duration.ofSeconds(1));
        assertThat(plan.passes()).allSatisfy(pass -> {
            assertThat(pass.end().time()).isAfter(MID_PASS.minus(ContactPlanner.LOOK_BACK));
            assertThat(pass.start().time()).isBefore(MID_PASS.plus(ContactPlanner.LOOK_AHEAD));
        });
        assertThat(plan.passes().getLast().start().time()).isAfter(MID_PASS.plus(Duration.ofHours(20)));
    }

    @Test
    @Requirement("CON-020")
    void unknownStationGivesAnUnknownPlan() {
        given(stations.byName("Kaunas")).willReturn(Optional.empty());

        assertThat(planner.plan(ISS, "Kaunas", MID_PASS)).isEqualTo(ContactPlan.UNKNOWN);
    }

    @Test
    @Requirement("CON-020")
    void linkWithoutAStationGivesAnUnknownPlan() {
        assertThat(planner.plan(ISS, null, MID_PASS)).isEqualTo(ContactPlan.UNKNOWN);
    }

    @Test
    @Requirement("CON-020")
    void satelliteWithoutOrbitDataGivesAnUnknownPlan() {
        given(stations.byName("Vilnius")).willReturn(Optional.of(VILNIUS));
        given(tles.latest(ISS)).willThrow(new OrbitDataUnavailableException("No orbit data stored for NORAD ID 25544"));

        assertThat(planner.plan(ISS, "Vilnius", MID_PASS)).isEqualTo(ContactPlan.UNKNOWN);
    }
}
