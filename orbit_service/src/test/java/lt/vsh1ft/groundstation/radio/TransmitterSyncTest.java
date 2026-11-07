package lt.vsh1ft.groundstation.radio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import lt.vsh1ft.groundstation.catalog.CatalogRefreshed;
import lt.vsh1ft.groundstation.catalog.SatelliteService;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.ResourceAccessException;

class TransmitterSyncTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final Duration INTERVAL = Duration.ofHours(24);
    private static final Instant UPDATED = Instant.parse("2019-06-16T08:49:33.586159Z");

    private static final SatnogsTransmitter ISS_APRS = transmitter("iss-aprs", 25544, "Transceiver");
    private static final SatnogsTransmitter NOAA_19_APT = transmitter("noaa-apt", 33591, "Transmitter");
    private static final SatnogsTransmitter OUTSIDE_CATALOG = transmitter("other", 99999, "Transmitter");

    private final SatnogsClient satnogs = mock(SatnogsClient.class);
    private final TransmitterService transmitters = mock(TransmitterService.class);
    private final SatelliteService catalog = mock(SatelliteService.class);
    private final TransmitterSync sync = new TransmitterSync(satnogs, transmitters, catalog,
            new TransmitterSyncProperties(INTERVAL), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void firstSyncStoresTheTransmittersOfCatalogSatellitesOnly() {
        given(transmitters.lastSync()).willReturn(Optional.empty());
        given(catalog.all()).willReturn(List.of(new NoradId(25544), new NoradId(33591)));
        given(satnogs.transmitters()).willReturn(List.of(ISS_APRS, OUTSIDE_CATALOG, NOAA_19_APT));

        sync.onCatalogRefreshed(new CatalogRefreshed());

        assertThat(replaced()).extracting(Transmitter::uuid, Transmitter::syncedAt)
                .containsExactly(tuple("iss-aprs", NOW), tuple("noaa-apt", NOW));
    }

    @Test
    void syncWaitsUntilTheIntervalHasPassed() {
        given(transmitters.lastSync()).willReturn(Optional.of(NOW.minus(Duration.ofHours(23))));

        sync.onCatalogRefreshed(new CatalogRefreshed());

        verifyNoInteractions(satnogs);
        verify(transmitters, never()).replaceAll(any());
    }

    @Test
    void syncRunsAgainOnceTheIntervalHasPassed() {
        given(transmitters.lastSync()).willReturn(Optional.of(NOW.minus(INTERVAL)));
        given(catalog.all()).willReturn(List.of(new NoradId(25544)));
        given(satnogs.transmitters()).willReturn(List.of(ISS_APRS));

        sync.onCatalogRefreshed(new CatalogRefreshed());

        assertThat(replaced()).extracting(Transmitter::uuid).containsExactly("iss-aprs");
    }

    @Test
    void transmitterWithAnUnknownTypeIsSkipped() {
        given(transmitters.lastSync()).willReturn(Optional.empty());
        given(catalog.all()).willReturn(List.of(new NoradId(25544)));
        given(satnogs.transmitters()).willReturn(List.of(transmitter("odd", 25544, "Repeater"), ISS_APRS));

        sync.onCatalogRefreshed(new CatalogRefreshed());

        assertThat(replaced()).extracting(Transmitter::uuid).containsExactly("iss-aprs");
    }

    @Test
    void failedDownloadKeepsTheStoredTransmitters() {
        given(transmitters.lastSync()).willReturn(Optional.empty());
        given(catalog.all()).willReturn(List.of(new NoradId(25544)));
        given(satnogs.transmitters()).willThrow(new ResourceAccessException("SatNOGS is down"));

        assertThatNoException().isThrownBy(() -> sync.onCatalogRefreshed(new CatalogRefreshed()));
        verify(transmitters, never()).replaceAll(any());
    }

    @SuppressWarnings("unchecked")
    private List<Transmitter> replaced() {
        final ArgumentCaptor<List<Transmitter>> captor = ArgumentCaptor.forClass(List.class);
        verify(transmitters).replaceAll(captor.capture());
        return captor.getValue();
    }

    private static SatnogsTransmitter transmitter(String uuid, int noradId, String type) {
        return new SatnogsTransmitter(uuid, noradId, "Beacon", type, "active", null, null, 437800000L, null, "FSK",
                null, 9600.0, false, UPDATED);
    }
}
