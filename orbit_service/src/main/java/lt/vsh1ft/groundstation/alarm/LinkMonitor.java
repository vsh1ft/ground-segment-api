package lt.vsh1ft.groundstation.alarm;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.xtce.MonitoringLevel;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class LinkMonitor {

    private final AlarmArchive alarms;

    @Transactional(propagation = Propagation.NESTED)
    public void started(AlarmKind kind, NoradId satellite, Instant at) {
        alarms.open(satellite, kind).stream().findFirst().ifPresentOrElse(
                open -> observe(open, MonitoringLevel.WARNING, at),
                () -> alarms.save(Alarm.onLink(kind, satellite, at)));
    }

    @Transactional(propagation = Propagation.NESTED)
    public void ended(AlarmKind kind, NoradId satellite, Instant at) {
        alarms.open(satellite, kind).forEach(open -> observe(open, MonitoringLevel.IN_LIMITS, at));
    }

    private void observe(Alarm open, MonitoringLevel level, Instant at) {
        final var observed = open.observe(level, at);
        if (!observed.equals(open)) {
            alarms.save(observed);
        }
    }
}
