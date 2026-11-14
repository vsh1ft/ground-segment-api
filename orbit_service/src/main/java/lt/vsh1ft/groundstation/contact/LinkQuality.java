package lt.vsh1ft.groundstation.contact;

import java.time.Instant;

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

    private final LinkReportArchive reports;
    private final ContactProperties properties;
    private final ApplicationEventPublisher events;

    private LinkTally tally;

    synchronized void frameReceived(NoradId satellite, Pass pass, Instant at, int lostBefore, boolean idle) {
        tallyOf(satellite, pass, at).received(lostBefore, idle);
    }

    synchronized void frameFailedCheck(NoradId satellite, Pass pass, Instant at) {
        tallyOf(satellite, pass, at).failedCheck();
    }

    synchronized void check(Pass passNow, Instant now) {
        if (tally == null) {
            return;
        }
        if (!tally.sameContact(passNow)) {
            finish();
            return;
        }
        judge(now);
        tally.reportDue(now).ifPresent(reports::save);
    }

    synchronized void noPass() {
        if (tally != null) {
            finish();
        }
    }

    private LinkTally tallyOf(NoradId satellite, Pass pass, Instant at) {
        if (tally != null && !tally.sameContact(pass)) {
            finish();
        }
        if (tally == null) {
            tally = new LinkTally(satellite, pass, at);
        }
        return tally;
    }

    private void judge(Instant now) {
        if (!tally.degradationChanged(properties.maxFrameLoss())) {
            return;
        }
        if (!tally.degraded()) {
            events.publishEvent(new LinkRecovered(tally.satellite(), now));
            return;
        }
        final var report = tally.report(now);
        log.warn("Satellite {} lost {} of {} frames in the pass that started at {}", tally.satellite(),
                report.framesLost(), report.framesReceived() + report.framesLost(), report.passStart());
        events.publishEvent(new LinkDegraded(tally.satellite(), now));
    }

    private void finish() {
        final var finished = tally;
        tally = null;
        reports.save(finished.finalReport());
        if (finished.degraded()) {
            events.publishEvent(new LinkRecovered(finished.satellite(), finished.passEnd()));
        }
    }
}
