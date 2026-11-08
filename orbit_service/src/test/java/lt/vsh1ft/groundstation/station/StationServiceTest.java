package lt.vsh1ft.groundstation.station;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import lt.vsh1ft.groundstation.PostgresTestcontainer;
import lt.vsh1ft.groundstation.pass.GroundStation;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainer.class, StationService.class})
class StationServiceTest {

    private static final GroundStation VILNIUS = new GroundStation("Vilnius", 54.67519, 25.23422, 114.0, 5.0);
    private static final GroundStation KAUNAS = new GroundStation("Kaunas", 54.89865, 23.90359, 75.0, 10.0);
    private static final UUID UNKNOWN = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private StationService service;

    @Test
    @Requirement("STN-010")
    void createdStationGetsAnIdAndCanBeReadBack() {
        final var created = service.create(VILNIUS);

        assertThat(created.id()).isNotNull();
        assertThat(service.get(created.id())).isEqualTo(created);
        assertThat(created.toGroundStation()).isEqualTo(VILNIUS);
    }

    @Test
    @Requirement("STN-010")
    void stationsAreListedByName() {
        service.create(VILNIUS);
        service.create(KAUNAS);

        assertThat(service.all()).extracting(Station::name).containsExactly("Kaunas", "Vilnius");
    }

    @Test
    @Requirement("STN-020")
    void secondStationWithTheSameNameIsRejected() {
        service.create(VILNIUS);

        assertThatThrownBy(() -> service.create(VILNIUS)).isInstanceOf(StationNameTakenException.class);
    }

    @Test
    @Requirement("STN-010")
    void updateReplacesEveryField() {
        final var created = service.create(VILNIUS);

        service.update(created.id(), KAUNAS);

        assertThat(service.get(created.id())).isEqualTo(Station.of(created.id(), KAUNAS));
    }

    @Test
    @Requirement("STN-020")
    void updateMayKeepTheStationsOwnName() {
        final var created = service.create(VILNIUS);
        final var lowerHorizon = new GroundStation("Vilnius", 54.67519, 25.23422, 114.0, 0.0);

        service.update(created.id(), lowerHorizon);

        assertThat(service.get(created.id()).minElevationDeg()).isEqualTo(0.0);
    }

    @Test
    @Requirement("STN-020")
    void updateToAnotherStationsNameIsRejected() {
        service.create(VILNIUS);
        final var kaunas = service.create(KAUNAS);

        assertThatThrownBy(() -> service.update(kaunas.id(), VILNIUS)).isInstanceOf(StationNameTakenException.class);
    }

    @Test
    @Requirement("STN-040")
    void updatingAnUnknownStationIsNotFound() {
        assertThatThrownBy(() -> service.update(UNKNOWN, VILNIUS)).isInstanceOf(StationNotFoundException.class);
    }

    @Test
    @Requirement("STN-010")
    void deletedStationIsGone() {
        final var created = service.create(VILNIUS);

        service.delete(created.id());

        assertThatThrownBy(() -> service.get(created.id())).isInstanceOf(StationNotFoundException.class);
    }

    @Test
    @Requirement("STN-040")
    void deletingAnUnknownStationIsNotFound() {
        assertThatThrownBy(() -> service.delete(UNKNOWN)).isInstanceOf(StationNotFoundException.class);
    }

    @Test
    @Requirement("CON-010")
    void stationIsFoundByItsExactName() {
        service.create(VILNIUS);

        assertThat(service.byName("Vilnius")).contains(VILNIUS);
        assertThat(service.byName("vilnius")).isEmpty();
    }
}
