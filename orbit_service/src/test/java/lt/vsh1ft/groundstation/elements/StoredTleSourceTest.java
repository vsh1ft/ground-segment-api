package lt.vsh1ft.groundstation.elements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.shared.OrekitDataLoader;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StoredTleSourceTest {

    private static final NoradId ISS = new NoradId(25544);

    private final StoredOmmRepository repository = mock(StoredOmmRepository.class);
    private final StoredTleSource source = new StoredTleSource(repository);

    @BeforeAll
    static void loadOrekitData() {
        new OrekitDataLoader(new File("orekit-data"));
    }

    @Test
    @Requirement("PASS-010")
    void newestStoredElementsAreUsed() {
        given(repository.findFirstByNoradIdOrderByEpochDesc(25544)).willReturn(Optional.of(OmmFixtures.ISS));

        final var tle = source.latest(ISS);

        assertThat(tle.getSatelliteNumber()).isEqualTo(25544);
        assertThat(tle.getDate().toInstant())
                .isCloseTo(OmmFixtures.ISS.epoch().toInstant(ZoneOffset.UTC), within(1, ChronoUnit.MILLIS));
    }

    @Test
    @Requirement("PASS-050")
    void satelliteWithNothingStoredIsUnavailable() {
        given(repository.findFirstByNoradIdOrderByEpochDesc(25544)).willReturn(Optional.empty());

        assertThatThrownBy(() -> source.latest(ISS)).isInstanceOf(OrbitDataUnavailableException.class)
                .hasMessage("No orbit data stored for NORAD ID 25544");
    }
}
