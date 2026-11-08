package lt.vsh1ft.groundstation.contact;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.groundstation.pass.Pass;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContactWatch {

    static final Duration CHECK_PERIOD = Duration.ofSeconds(1);

    private final ContactPlanner planner;
    private final LinkQuality linkQuality;
    private final ContactProperties properties;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    private NoradId satellite;
    private String station;
    private ContactPlan plan = ContactPlan.UNKNOWN;
    private Instant lastPacket;
    private Pass silentPass;

    public void watch(NoradId linkSatellite, String linkStation) {
        synchronized (this) {
            satellite = linkSatellite;
            station = linkStation;
            lastPacket = clock.instant();
        }
        replan();
        scheduler.scheduleWithFixedDelay(this::replan, clock.instant().plus(properties.planInterval()),
                properties.planInterval());
        scheduler.scheduleAtFixedRate(this::check, CHECK_PERIOD);
        log.info("Watching contacts of satellite {} over station {}; {} without a packet during a pass is a loss "
                + "of signal", linkSatellite, linkStation, properties.silenceThreshold());
    }

    public Visibility received(NoradId from, Instant at) {
        final Visibility visibility;
        final boolean silenceEnds;
        synchronized (this) {
            if (!from.equals(satellite)) {
                return Visibility.UNKNOWN;
            }
            lastPacket = at;
            visibility = plan.visibility(at);
            silenceEnds = silentPass != null;
            silentPass = null;
        }
        if (silenceEnds) {
            events.publishEvent(new SilenceEnded(from, at));
        }
        return visibility;
    }

    public void frameReceived(NoradId from, Instant at, int lostBefore, boolean idle) {
        if (isLink(from)) {
            linkQuality.frameReceived(from, passAt(at), at, lostBefore, idle);
        }
    }

    public void frameFailedCheck(NoradId from, Instant at) {
        if (isLink(from)) {
            linkQuality.frameFailedCheck(from, passAt(at), at);
        }
    }

    private synchronized boolean isLink(NoradId from) {
        return from.equals(satellite);
    }

    private synchronized Optional<Pass> passAt(Instant at) {
        return plan.passAt(at);
    }

    void replan() {
        final NoradId linkSatellite;
        final String linkStation;
        synchronized (this) {
            linkSatellite = satellite;
            linkStation = station;
        }
        final var made = planner.plan(linkSatellite, linkStation, clock.instant());
        synchronized (this) {
            plan = made;
        }
    }

    void check() {
        final var now = clock.instant();
        final Object event;
        final Optional<Pass> passNow;
        synchronized (this) {
            event = silentPass == null ? silenceStarting(now) : silenceEnding(now);
            passNow = plan.passAt(now);
        }
        if (event != null) {
            events.publishEvent(event);
        }
        linkQuality.check(passNow, now);
    }

    private SilenceStarted silenceStarting(Instant now) {
        final var pass = plan.passAt(now);
        if (pass.isEmpty()) {
            return null;
        }
        final var start = pass.get().start().time();
        final var silentSince = lastPacket.isAfter(start) ? lastPacket : start;
        if (now.isBefore(silentSince.plus(properties.silenceThreshold()))) {
            return null;
        }
        silentPass = pass.get();
        log.warn("No packet from satellite {} since {}, during the pass that ends at {}", satellite, silentSince,
                silentPass.end().time());
        return new SilenceStarted(satellite, now);
    }

    private SilenceEnded silenceEnding(Instant now) {
        final var end = silentPass.end().time();
        if (now.isBefore(end)) {
            return null;
        }
        silentPass = null;
        return new SilenceEnded(satellite, end);
    }
}
