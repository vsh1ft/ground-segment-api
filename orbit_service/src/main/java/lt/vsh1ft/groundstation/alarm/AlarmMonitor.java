package lt.vsh1ft.groundstation.alarm;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.tmtc.ParameterValuesReceived;
import lt.vsh1ft.xtce.EngineeringValue;
import lt.vsh1ft.xtce.MonitoringLevel;
import lt.vsh1ft.xtce.ParameterValue;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class AlarmMonitor {

    private final AlarmArchive alarms;

    @Transactional(propagation = Propagation.NESTED)
    public void check(ParameterValuesReceived received) {
        final Map<String, Alarm> open = alarms.open(received.satellite()).stream()
                .collect(Collectors.toMap(Alarm::parameter, Function.identity()));
        for (var value : received.values()) {
            check(received.satellite(), value, received.generationTime(),
                    Optional.ofNullable(open.get(value.parameter().name())));
        }
    }

    private void check(NoradId satellite, ParameterValue value, Instant at, Optional<Alarm> open) {
        final var level = value.level();
        open.ifPresentOrElse(alarm -> {
            final var observed = alarm.observe(level, at);
            if (!observed.equals(alarm)) {
                alarms.save(observed);
            }
        }, () -> {
            if (level != MonitoringLevel.IN_LIMITS) {
                alarms.save(Alarm.raise(satellite, value.parameter().name(), level, at,
                        value.engineering().flatMap(EngineeringValue::number).orElseThrow()));
            }
        });
    }
}
