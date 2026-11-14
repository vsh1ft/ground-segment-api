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

    private volatile NoradId satellite;
    private volatile String station;
    private SilenceDetector silence;
    private ContactPlan plan = ContactPlan.UNKNOWN;

    public void watch(NoradId linkSatellite, String linkStation) {
        synchronized (this) {
            satellite = linkSatellite;
            station = linkStation;
            silence = new SilenceDetector(linkSatellite, properties.silenceThreshold(), clock.instant());
        }
        replan();
        scheduler.scheduleWithFixedDelay(this::replan, clock.instant().plus(properties.planInterval()),
                properties.planInterval());
        scheduler.scheduleAtFixedRate(this::check, CHECK_PERIOD);
        log.info("Watching contacts of satellite {} over station {}; {} without a packet during a pass is a loss "
                + "of signal", linkSatellite, linkStation, properties.silenceThreshold());
    }

    public synchronized Visibility received(NoradId from, Instant at) {
        if (!from.equals(satellite)) {
            return Visibility.UNKNOWN;
        }
        silence.packetReceived(at).ifPresent(events::publishEvent);
        return plan.visibility(at);
    }

    public synchronized void frameReceived(NoradId from, Instant at, int lostBefore, boolean idle) {
        passOfLink(from, at).ifPresent(pass -> linkQuality.frameReceived(from, pass, at, lostBefore, idle));
    }

    public synchronized void frameFailedCheck(NoradId from, Instant at) {
        passOfLink(from, at).ifPresent(pass -> linkQuality.frameFailedCheck(from, pass, at));
    }

    void replan() {
        final var made = planner.plan(satellite, station, clock.instant());
        synchronized (this) {
            plan = made;
        }
    }

    synchronized void check() {
        final var now = clock.instant();
        final var passNow = plan.passAt(now);
        silence.check(passNow, now).ifPresent(events::publishEvent);
        passNow.ifPresentOrElse(pass -> linkQuality.check(pass, now), linkQuality::noPass);
    }

    private Optional<Pass> passOfLink(NoradId from, Instant at) {
        return from.equals(satellite) ? plan.passAt(at) : Optional.empty();
    }
}
