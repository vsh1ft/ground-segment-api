package lt.vsh1ft.groundstation.alarm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import lt.vsh1ft.xtce.MonitoringLevel;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class AlarmServiceTest {

    private static final NoradId SATELLITE = new NoradId(99999);
    private static final Instant T0 = Instant.parse("2026-11-08T09:00:01Z");
    private static final Instant NOW = Instant.parse("2026-11-08T09:30:00Z");
    private static final Alarm TRIGGERED = new Alarm(3L, 0L, 99999, "BATTERY_VOLTAGE", MonitoringLevel.WARNING,
            AlarmState.TRIGGERED, T0, 7.199, null, null, null);

    private final AlarmArchive alarms = mock(AlarmArchive.class);
    private final AlarmService service = new AlarmService(alarms, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @Requirement("ALM-050")
    void acknowledgementIsTimedByTheGroundClockAndSaved() {
        final var acknowledged = TRIGGERED.acknowledge(NOW);
        given(alarms.find(SATELLITE, 3L)).willReturn(Optional.of(TRIGGERED));
        given(alarms.save(acknowledged)).willReturn(acknowledged);

        final var response = service.acknowledge(SATELLITE, 3L);

        assertThat(response.state()).isEqualTo(AlarmState.ACKNOWLEDGED);
        assertThat(response.acknowledgedAt()).isEqualTo(NOW);
    }

    @Test
    @Requirement("ALM-040")
    void acknowledgingAnAcknowledgedAlarmWritesNothing() {
        final var acknowledged = TRIGGERED.acknowledge(T0);
        given(alarms.find(SATELLITE, 3L)).willReturn(Optional.of(acknowledged));

        assertThat(service.acknowledge(SATELLITE, 3L).acknowledgedAt()).isEqualTo(T0);
        verify(alarms, never()).save(any());
    }

    @Test
    @Requirement("ALM-050")
    void unknownAlarmIsNotFound() {
        given(alarms.find(SATELLITE, 4L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.acknowledge(SATELLITE, 4L)).isInstanceOf(UnknownAlarmException.class)
                .hasMessage("No alarm 4 for satellite 99999");
    }

    @Test
    @Requirement("ALM-050")
    void alarmChangedByNewTelemetryDuringTheAcknowledgementIsAConflict() {
        given(alarms.find(SATELLITE, 3L)).willReturn(Optional.of(TRIGGERED));
        given(alarms.save(any())).willThrow(new OptimisticLockingFailureException("version 0 is outdated"));

        assertThatThrownBy(() -> service.acknowledge(SATELLITE, 3L)).isInstanceOf(AlarmChangedException.class)
                .hasMessage("Alarm 3 changed while it was being acknowledged; read it again and retry");
    }
}
