package lt.vsh1ft.groundstation.alarm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.groundstation.tmtc.ParameterValuesReceived;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class AlarmListener {

    private final AlarmMonitor monitor;

    @EventListener
    void on(ParameterValuesReceived received) {
        try {
            monitor.check(received);
        } catch (RuntimeException e) {
            log.error("Alarms not updated for packet {} of satellite {}; its values stay unchecked",
                    received.packetId(), received.satellite(), e);
        }
    }
}
