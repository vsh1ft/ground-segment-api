package lt.vsh1ft.groundstation.radio;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.groundstation.catalog.CatalogRefreshed;
import lt.vsh1ft.groundstation.catalog.SatelliteService;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class TransmitterSync {

    private final SatnogsClient satnogs;
    private final TransmitterService transmitters;
    private final SatelliteService catalog;
    private final TransmitterSyncProperties properties;
    private final Clock clock;

    @EventListener
    public void onCatalogRefreshed(CatalogRefreshed event) {
        final var now = clock.instant();
        final var due = transmitters.lastSync().map(last -> last.plus(properties.interval())).orElse(now);
        if (due.isAfter(now)) {
            return;
        }
        sync(now);
    }

    private void sync(Instant now) {
        try {
            final var inCatalog = catalog.all().stream().map(NoradId::value).collect(Collectors.toSet());
            final var synced = satnogs.transmitters().stream()
                    .filter(transmitter -> inCatalog.contains(transmitter.noradCatId()))
                    .flatMap(transmitter -> usable(transmitter, now).stream())
                    .toList();
            transmitters.replaceAll(synced);
            log.info("Synced {} transmitters from SatNOGS", synced.size());
        } catch (RuntimeException e) {
            log.warn("Transmitter sync from SatNOGS failed: {}", e.getMessage());
        }
    }

    private static Optional<Transmitter> usable(SatnogsTransmitter transmitter, Instant syncedAt) {
        try {
            return Optional.of(transmitter.toTransmitter(syncedAt));
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Skipping SatNOGS transmitter {}: {}", transmitter.uuid(), e.getMessage());
            return Optional.empty();
        }
    }
}
