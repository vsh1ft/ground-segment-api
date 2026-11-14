package lt.vsh1ft.groundstation.contact;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import lt.vsh1ft.groundstation.pass.Pass;
import lt.vsh1ft.groundstation.shared.NoradId;

final class LinkTally {

    static final Duration SAVE_INTERVAL = Duration.ofSeconds(10);
    static final long MIN_EXPECTED_FRAMES = 100;

    private final NoradId satellite;
    private final Pass pass;
    private Instant savedAt;
    private long received;
    private long lost;
    private long failedCheck;
    private long idle;
    private boolean degraded;

    LinkTally(NoradId satellite, Pass pass, Instant startedAt) {
        this.satellite = satellite;
        this.pass = pass;
        this.savedAt = startedAt;
    }

    NoradId satellite() {
        return satellite;
    }

    Instant passEnd() {
        return pass.end().time();
    }

    boolean degraded() {
        return degraded;
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

    boolean degradationChanged(double maxFrameLoss) {
        if (received + lost < MIN_EXPECTED_FRAMES) {
            return false;
        }
        final var degradedNow = report(savedAt).lossRatio() > maxFrameLoss;
        final var changed = degradedNow != degraded;
        degraded = degradedNow;
        return changed;
    }

    Optional<LinkReport> reportDue(Instant now) {
        if (now.isBefore(savedAt.plus(SAVE_INTERVAL))) {
            return Optional.empty();
        }
        savedAt = now;
        return Optional.of(report(now));
    }

    LinkReport finalReport() {
        return report(passEnd());
    }

    LinkReport report(Instant updatedAt) {
        return new LinkReport(satellite.value(), pass.start().time(), pass.end().time(), received, lost,
                failedCheck, idle, updatedAt);
    }
}
