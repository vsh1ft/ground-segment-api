package lt.vsh1ft.groundstation.tle;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import lt.vsh1ft.groundstation.pass.NoradId;
import lt.vsh1ft.groundstation.pass.OrbitDataUnavailableException;
import lt.vsh1ft.groundstation.pass.SatelliteNotFoundException;
import org.junit.jupiter.api.Test;

class StoredTleSourceTest {

    private static final NoradId SATELLITE = new NoradId(99999);

    private final CelestrakClient client = mock(CelestrakClient.class);
    private final StoredOmmRepository repository = mock(StoredOmmRepository.class);
    private final StoredTleSource source = new StoredTleSource(
            client,
            new CelestrakProperties("https://celestrak.org", Duration.ofHours(2), Duration.ofSeconds(5), Duration.ofSeconds(10)),
            repository,
            Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void unknownSatelliteWithNothingStoredIsNotFound() {
        given(repository.findFirstByNoradIdOrderByDownloadedAtDesc(99999)).willReturn(Optional.empty());
        given(client.latest(SATELLITE)).willThrow(new SatelliteNotFoundException(SATELLITE));

        assertThatThrownBy(() -> source.latest(SATELLITE)).isInstanceOf(SatelliteNotFoundException.class);
    }

    @Test
    void satelliteThatCelestrakNoLongerKnowsIsNotFoundEvenWithStoredData() {
        final var stale = OmmFixtures.ISS.withDownloadedAt(Instant.parse("2026-10-01T00:00:00Z"));
        given(repository.findFirstByNoradIdOrderByDownloadedAtDesc(99999)).willReturn(Optional.of(stale));
        given(client.latest(SATELLITE)).willThrow(new SatelliteNotFoundException(SATELLITE));

        assertThatThrownBy(() -> source.latest(SATELLITE)).isInstanceOf(SatelliteNotFoundException.class);
    }

    @Test
    void celestrakFailureWithNothingStoredIsUnavailable() {
        given(repository.findFirstByNoradIdOrderByDownloadedAtDesc(99999)).willReturn(Optional.empty());
        given(client.latest(SATELLITE)).willThrow(new CelestrakException("CelesTrak request for 99999 failed"));

        assertThatThrownBy(() -> source.latest(SATELLITE)).isInstanceOf(OrbitDataUnavailableException.class);
    }
}
