package lt.vsh1ft.groundstation.contact;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.groundstation.pass.Pass;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class LinkQuality {

    static final Duration SAVE_INTERVAL = Duration.ofSeconds(10);
    static final long MIN_EXPECTED_FRAMES = 100;

    private final LinkReportArchive reports;
    private final ContactProperties properties;
    private final ApplicationEventPublisher events;

    private Tally tally;

    void frameReceived(NoradId satellite, Optional<Pass> pass, Instant at, int lostBefore, boolean idle) {
        pass.ifPresent(during -> update(satellite, during, at, current -> current.received(lostBefore, idle)));
    }

    void frameFailedCheck(NoradId satellite, Optional<Pass> pass, Instant at) {
        pass.ifPresent(during -> update(satellite, during, at, Tally::failedCheck));
    }

    void check(Optional<Pass> passNow, Instant now) {
        final List<Runnable> actions = new ArrayList<>();
        synchronized (this) {
            if (tally == null) {
                return;
            }
            if (passNow.filter(tally::sameContact).isEmpty()) {
                actions.addAll(finish());
            } else {
                actions.addAll(judge(now));
                if (!now.isBefore(tally.savedAt.plus(SAVE_INTERVAL))) {
                    tally.savedAt = now;
                    final var report = tally.report(now);
                    actions.add(() -> reports.save(report));
                }
            }
        }
        actions.forEach(Runnable::run);
    }

    private void update(NoradId satellite, Pass pass, Instant at, Consumer<Tally> change) {
        final List<Runnable> actions = new ArrayList<>();
        synchronized (this) {
            if (tally != null && !tally.sameContact(pass)) {
                actions.addAll(finish());
            }
            if (tally == null) {
                tally = new Tally(satellite, pass, at);
            }
            change.accept(tally);
        }
        actions.forEach(Runnable::run);
    }

    private List<Runnable> judge(Instant now) {
        final var report = tally.report(now);
        if (report.framesReceived() + report.framesLost() < MIN_EXPECTED_FRAMES) {
            return List.of();
        }
        final var degraded = report.lossRatio() > properties.maxFrameLoss();
        if (degraded == tally.degraded) {
            return List.of();
        }
        tally.degraded = degraded;
        final var satellite = tally.satellite;
        if (degraded) {
            log.warn("Satellite {} lost {} of {} frames in the pass that started at {}", satellite,
                    report.framesLost(), report.framesReceived() + report.framesLost(), report.passStart());
            return List.of(() -> events.publishEvent(new LinkDegraded(satellite, now)));
        }
        return List.of(() -> events.publishEvent(new LinkRecovered(satellite, now)));
    }

    private List<Runnable> finish() {
        final var finished = tally;
        tally = null;
        final var end = finished.pass.end().time();
        final var report = finished.report(end);
        final List<Runnable> actions = new ArrayList<>();
        actions.add(() -> reports.save(report));
        if (finished.degraded) {
            actions.add(() -> events.publishEvent(new LinkRecovered(finished.satellite, end)));
        }
        return actions;
    }

    private static final class Tally {

        private final NoradId satellite;
        private final Pass pass;
        private Instant savedAt;
        private long received;
        private long lost;
        private long failedCheck;
        private long idle;
        private boolean degraded;

        Tally(NoradId satellite, Pass pass, Instant startedAt) {
            this.satellite = satellite;
            this.pass = pass;
            this.savedAt = startedAt;
        }

        boolean sameContact(Pass other) {
            return other.start().time().isBefore(pass.end().time()) && pass.start().time().isBefore(other.end().time());
        }

        void received(int lostBefore, boolean idleFrame) {
            received++;
            lost += lostBefore;
            if (idleFrame) {
                idle++;
            }
        }

        void failedCheck() {
            failedCheck++;
        }

        LinkReport report(Instant updatedAt) {
            return new LinkReport(satellite.value(), pass.start().time(), pass.end().time(), received, lost,
                    failedCheck, idle, updatedAt);
        }
    }
}
