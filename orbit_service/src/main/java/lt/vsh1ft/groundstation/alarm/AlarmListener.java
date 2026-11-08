package lt.vsh1ft.groundstation.alarm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.groundstation.contact.SilenceEnded;
import lt.vsh1ft.groundstation.contact.SilenceStarted;
import lt.vsh1ft.groundstation.tmtc.ParameterValuesReceived;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class AlarmListener {

    private final AlarmMonitor monitor;
    private final SignalMonitor signal;

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
        try {
            signal.silenceStarted(silence);
        } catch (RuntimeException e) {
            log.error("Loss of signal of satellite {} at {} not raised as an alarm", silence.satellite(),
                    silence.at(), e);
        }
    }

    @EventListener
    void on(SilenceEnded silence) {
        try {
            signal.silenceEnded(silence);
        } catch (RuntimeException e) {
            log.error("End of the loss of signal of satellite {} at {} not applied to its alarm",
                    silence.satellite(), silence.at(), e);
        }
    }
}
