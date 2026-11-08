package lt.vsh1ft.groundstation.satnogs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.groundstation.catalog.SatelliteNotInCatalogException;
import lt.vsh1ft.groundstation.catalog.SatelliteService;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.tmtc.FrameReception;
import lt.vsh1ft.groundstation.tmtc.Reception;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@Component
@RequiredArgsConstructor
class SatnogsImport {

    private final SatnogsNetworkClient client;
    private final SatnogsImportState state;
    private final FrameReception reception;
    private final SatelliteService catalog;
    private final SatnogsImportProperties properties;
    private final TaskScheduler scheduler;
    private final Clock clock;

    private Instant pausedUntil = Instant.MIN;

    @EventListener(ApplicationReadyEvent.class)
    public void schedule() {
        if (properties.satellites().isEmpty()) {
            log.info("No satellites to import from SatNOGS");
            return;
        }
        log.info("Importing frames of satellites {} from SatNOGS every {}", properties.satellites(),
                properties.interval());
        scheduler.scheduleWithFixedDelay(this::importAll, clock.instant(), properties.interval());
    }

    void importAll() {
        for (var noradId : properties.satellites()) {
            if (clock.instant().isBefore(pausedUntil)) {
                log.info("SatNOGS asked to wait until {}; not importing satellite {} now", pausedUntil, noradId);
                continue;
            }
            importSatellite(new NoradId(noradId));
        }
    }

    private void importSatellite(NoradId satellite) {
        try {
            catalog.ensureInCatalog(satellite);
            final var now = clock.instant();
            final var run = new Run(satellite, state.importedUntil(satellite).orElse(now.minus(properties.lookBack())),
                    now.minus(properties.settle()));
            run.importObservations();
            if (run.failedFrames == 0) {
                state.save(satellite, run.until);
            }
            log.info("SatNOGS import of satellite {} up to {}: {} observations, {} frames, {} new; {} failed",
                    satellite, run.until, run.observations, run.frames, run.newFrames, run.failedFrames);
        } catch (HttpClientErrorException.TooManyRequests e) {
            final var wait = retryAfter(e);
            pausedUntil = clock.instant().plus(wait);
            log.warn("SatNOGS throttled the import of satellite {}; waiting {} before asking again", satellite, wait);
        } catch (SatelliteNotInCatalogException e) {
            log.warn("Not importing SatNOGS frames of satellite {}: {}", satellite, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("SatNOGS import of satellite {} failed; it starts again from the same point next time: {}",
                    satellite, e.toString());
        }
    }

    private Duration retryAfter(HttpClientErrorException.TooManyRequests e) {
        final var header = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After");
        if (header == null || !header.matches("\\d+")) {
            return properties.interval();
        }
        return Duration.ofSeconds(Long.parseLong(header));
    }

    private final class Run {

        private final NoradId satellite;
        private final Instant from;
        private final Instant until;
        private int observations;
        private int frames;
        private int newFrames;
        private int failedFrames;

        Run(NoradId satellite, Instant from, Instant until) {
            this.satellite = satellite;
            this.from = from;
            this.until = until;
        }

        void importObservations() {
            String next = null;
            do {
                final var page = client.observations(satellite, next);
                for (var observation : page.observations()) {
                    if (!observation.end().isAfter(from)) {
                        return;
                    }
                    if (!observation.end().isAfter(until)) {
                        importObservation(observation);
                    }
                }
                next = page.next();
            } while (next != null);
        }

        private void importObservation(SatnogsObservation observation) {
            observations++;
            for (var file : observation.demoddata()) {
                try {
                    final var url = file.payloadDemod();
                    final var receivedAt = SatnogsFrameFile.receivedAt(url).orElse(observation.start());
                    final var outcome = reception.receive(satellite, SatnogsFrameFile.frame(client.download(url)),
                            Reception.satnogs(observation.stationName(), observation.stationLat(),
                                    observation.stationLng(), observation.stationAlt(), observation.id(),
                                    receivedAt));
                    frames++;
                    if (outcome.newFrame()) {
                        newFrames++;
                    }
                } catch (RuntimeException e) {
                    failedFrames++;
                    log.warn("Frame {} of SatNOGS observation {} not imported: {}", file.payloadDemod(),
                            observation.id(), e.toString());
                }
            }
        }
    }
}
