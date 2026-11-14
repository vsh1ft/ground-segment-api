package lt.vsh1ft.groundstation.satnogs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.groundstation.catalog.SatelliteNotInCatalogException;
import lt.vsh1ft.groundstation.catalog.SatelliteService;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.frame.FrameReception;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SatnogsImport {

    static final Duration CHUNK = Duration.ofHours(1);
    static final Duration OVERLAP = Duration.ofMinutes(30);

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
        final var run = new Run(satellite);
        try {
            catalog.ensureInCatalog(satellite);
            final var now = clock.instant();
            final var until = now.minus(properties.settle());
            var from = state.importedUntil(satellite).orElse(now.minus(properties.lookBack()));
            while (from.isBefore(until)) {
                final var to = earlier(from.plus(CHUNK), until);
                run.importChunk(from.minus(OVERLAP), to);
                state.save(satellite, to);
                from = to;
            }
            log.info("SatNOGS import of satellite {} up to {}: {}", satellite, until, run);
        } catch (SatnogsThrottledException e) {
            final var wait = e.retryAfter().orElse(properties.interval());
            pausedUntil = clock.instant().plus(wait);
            log.warn("SatNOGS throttled the import of satellite {}; waiting {} before asking again; so far {}",
                    satellite, wait, run);
        } catch (SatelliteNotInCatalogException e) {
            log.warn("Not importing SatNOGS frames of satellite {}: {}", satellite, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("SatNOGS import of satellite {} stopped; it continues from the last finished hour next time: {}; "
                    + "so far {}", satellite, e.toString(), run);
        }
    }

    private static Instant earlier(Instant one, Instant other) {
        return one.isBefore(other) ? one : other;
    }

    private final class Run {

        private final NoradId satellite;
        private int observations;
        private int frames;
        private int newFrames;
        private int skippedFrames;

        Run(NoradId satellite) {
            this.satellite = satellite;
        }

        void importChunk(Instant start, Instant end) {
            String next = null;
            do {
                final var page = client.observations(satellite, start, end, next);
                page.observations().forEach(this::importObservation);
                next = page.next();
            } while (next != null);
        }

        @Override
        public String toString() {
            return observations + " observations, " + frames + " frames, " + newFrames + " new, " + skippedFrames
                    + " skipped";
        }

        private void importObservation(SatnogsObservation observation) {
            observations++;
            for (var file : observation.demoddata()) {
                try {
                    final var url = file.payloadDemod();
                    final var receivedAt = SatnogsFrameFile.receivedAt(url).orElse(observation.start());
                    final var outcome = reception.receive(satellite, SatnogsFrameFile.frame(client.download(url)),
                            observation.receivedAt(receivedAt));
                    frames++;
                    if (outcome.newFrame()) {
                        newFrames++;
                    }
                } catch (RuntimeException e) {
                    skippedFrames++;
                    log.warn("Frame {} of SatNOGS observation {} not imported: {}", file.payloadDemod(),
                            observation.id(), e.toString());
                }
            }
        }
    }
}
