package lt.vsh1ft.groundstation.alarm;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.groundstation.contact.LinkDegraded;
import lt.vsh1ft.groundstation.contact.LinkRecovered;
import lt.vsh1ft.groundstation.contact.SilenceEnded;
import lt.vsh1ft.groundstation.contact.SilenceStarted;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.tmtc.ParameterValuesReceived;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class AlarmListener {

    private final AlarmMonitor monitor;
    private final LinkMonitor link;

    @EventListener
    void on(ParameterValuesReceived received) {
        try {
            monitor.check(received);
        } catch (RuntimeException e) {
            log.error("Alarms not updated for packet {} of satellite {}; its values stay unchecked",
                    received.packetId(), received.satellite(), e);
        }
    }

    @EventListener
    void on(SilenceStarted silence) {
        started(AlarmKind.LOSS_OF_SIGNAL, silence.satellite(), silence.at());
    }

    @EventListener
    void on(SilenceEnded silence) {
        ended(AlarmKind.LOSS_OF_SIGNAL, silence.satellite(), silence.at());
    }

    @EventListener
    void on(LinkDegraded degraded) {
        started(AlarmKind.LINK_QUALITY, degraded.satellite(), degraded.at());
    }

    @EventListener
    void on(LinkRecovered recovered) {
        ended(AlarmKind.LINK_QUALITY, recovered.satellite(), recovered.at());
    }

    private void started(AlarmKind kind, NoradId satellite, Instant at) {
        try {
            link.started(kind, satellite, at);
        } catch (RuntimeException e) {
            log.error("{} of satellite {} at {} not raised as an alarm", kind, satellite, at, e);
        }
    }

    private void ended(AlarmKind kind, NoradId satellite, Instant at) {
        try {
            link.ended(kind, satellite, at);
        } catch (RuntimeException e) {
            log.error("End of {} of satellite {} at {} not applied to its alarm", kind, satellite, at, e);
        }
    }
}
