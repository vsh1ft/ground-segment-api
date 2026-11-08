package lt.vsh1ft.groundstation.alarm;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.contact.SilenceEnded;
import lt.vsh1ft.groundstation.contact.SilenceStarted;
import lt.vsh1ft.xtce.MonitoringLevel;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class SignalMonitor {

    private final AlarmArchive alarms;

    @Transactional(propagation = Propagation.NESTED)
    public void silenceStarted(SilenceStarted silence) {
        alarms.open(silence.satellite(), AlarmKind.LOSS_OF_SIGNAL).stream().findFirst().ifPresentOrElse(
                open -> observe(open, MonitoringLevel.WARNING, silence.at()),
                () -> alarms.save(Alarm.lossOfSignal(silence.satellite(), silence.at())));
    }

    @Transactional(propagation = Propagation.NESTED)
    public void silenceEnded(SilenceEnded silence) {
        alarms.open(silence.satellite(), AlarmKind.LOSS_OF_SIGNAL)
                .forEach(open -> observe(open, MonitoringLevel.IN_LIMITS, silence.at()));
    }

    private void observe(Alarm open, MonitoringLevel level, Instant at) {
        final var observed = open.observe(level, at);
        if (!observed.equals(open)) {
            alarms.save(observed);
        }
    }
}
