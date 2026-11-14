package lt.vsh1ft.groundstation.contact;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.groundstation.pass.Pass;
import lt.vsh1ft.groundstation.shared.NoradId;

@Slf4j
final class SilenceDetector {

    private final NoradId satellite;
    private final Duration threshold;
    private Instant lastPacket;
    private Pass silentPass;

    SilenceDetector(NoradId satellite, Duration threshold, Instant watchedSince) {
        this.satellite = satellite;
        this.threshold = threshold;
        this.lastPacket = watchedSince;
    }

    Optional<SilenceEnded> packetReceived(Instant at) {
        lastPacket = at;
        if (silentPass == null) {
            return Optional.empty();
        }
        silentPass = null;
        return Optional.of(new SilenceEnded(satellite, at));
    }

    Optional<SilenceChange> check(Optional<Pass> passNow, Instant now) {
        if (silentPass != null) {
            return ending(now);
        }
        return passNow.flatMap(pass -> starting(pass, now));
    }

    private Optional<SilenceChange> starting(Pass pass, Instant now) {
        final var start = pass.start().time();
        final var silentSince = lastPacket.isAfter(start) ? lastPacket : start;
        if (now.isBefore(silentSince.plus(threshold))) {
            return Optional.empty();
        }
        silentPass = pass;
        log.warn("No packet from satellite {} since {}, during the pass that ends at {}", satellite, silentSince,
                pass.end().time());
        return Optional.of(new SilenceStarted(satellite, now));
    }

    private Optional<SilenceChange> ending(Instant now) {
        final var end = silentPass.end().time();
        if (now.isBefore(end)) {
            return Optional.empty();
        }
        silentPass = null;
        return Optional.of(new SilenceEnded(satellite, end));
    }
}
