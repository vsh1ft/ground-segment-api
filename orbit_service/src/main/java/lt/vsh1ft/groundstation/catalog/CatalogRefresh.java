package lt.vsh1ft.groundstation.catalog;

import java.time.Clock;
import java.util.HashSet;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.groundstation.elements.OmmArchive;
import lt.vsh1ft.groundstation.elements.OmmDownloader;
import lt.vsh1ft.groundstation.elements.SatelliteNotFoundException;
import lt.vsh1ft.groundstation.elements.StoredOmm;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class CatalogRefresh {

    private final OmmDownloader downloader;
    private final OmmArchive archive;
    private final SatelliteService catalog;
    private final CatalogRefreshProperties properties;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    @EventListener(ApplicationReadyEvent.class)
    public void schedule() {
        final var now = clock.instant();
        final var first = archive.lastDownload()
                .map(last -> last.plus(properties.interval()))
                .filter(due -> due.isAfter(now))
                .orElse(now);
        log.info("First catalog refresh at {}, then every {}", first, properties.interval());
        scheduler.scheduleWithFixedDelay(this::refresh, first, properties.interval());
    }

    public void refresh() {
        final var run = new Run();
        properties.groups().forEach(run::importGroup);
        catalog.all().stream().filter(run::notStoredYet).forEach(run::refreshOne);
        log.info("Catalog refresh finished: {} satellites stored, {} failures", run.stored.size(), run.failures);
        events.publishEvent(new CatalogRefreshed());
    }

    private final class Run {

        private final Set<NoradId> stored = new HashSet<>();
        private int failures;

        void importGroup(String group) {
            try {
                downloader.downloadGroup(group).forEach(this::store);
            } catch (RuntimeException e) {
                failed("group " + group, e);
            }
        }

        boolean notStoredYet(NoradId satellite) {
            return !stored.contains(satellite);
        }

        void refreshOne(NoradId satellite) {
            try {
                store(downloader.download(satellite));
            } catch (SatelliteNotFoundException e) {
                log.info("NORAD ID {} is not on CelesTrak yet; it keeps its stored elements", satellite);
            } catch (RuntimeException e) {
                failed("NORAD ID " + satellite, e);
            }
        }

        private void store(StoredOmm omm) {
            try {
                catalog.store(omm);
                stored.add(new NoradId(omm.noradId()));
            } catch (RuntimeException e) {
                failed("NORAD ID " + omm.noradId(), e);
            }
        }

        private void failed(String what, RuntimeException e) {
            failures++;
            log.warn("Catalog refresh of {} failed: {}", what, e.getMessage());
        }
    }
}
