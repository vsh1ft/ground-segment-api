package lt.vsh1ft.groundstation.alarm;

import java.time.Instant;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.xtce.MonitoringLevel;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

@Table("alarm")
record Alarm(
        @Id Long id,
        @Version Long version,
        int noradId,
        AlarmKind kind,
        String parameter,
        MonitoringLevel level,
        AlarmState state,
        Instant triggeredAt,
        Double triggerValue,
        Instant returnedAt,
        Instant acknowledgedAt,
        Instant clearedAt) {

    Alarm {
        if (level == MonitoringLevel.IN_LIMITS) {
            throw new IllegalArgumentException("An alarm is a warning or critical, not " + level);
        }
        final boolean hasParameterAndValue = parameter != null && triggerValue != null;
        if ((kind == AlarmKind.LIMIT) != hasParameterAndValue) {
            throw new IllegalArgumentException("Only a limit alarm has a parameter and a trigger value: " + kind
                    + ", " + parameter + ", " + triggerValue);
        }
    }

    static Alarm raise(NoradId satellite, String parameter, MonitoringLevel level, Instant at, double value) {
        return new Alarm(null, null, satellite.value(), AlarmKind.LIMIT, parameter, level, AlarmState.TRIGGERED, at,
                value, null, null, null);
    }

    static Alarm lossOfSignal(NoradId satellite, Instant at) {
        return new Alarm(null, null, satellite.value(), AlarmKind.LOSS_OF_SIGNAL, null, MonitoringLevel.WARNING,
                AlarmState.TRIGGERED, at, null, null, null, null);
    }

    Alarm observe(MonitoringLevel observed, Instant at) {
        if (observed == MonitoringLevel.IN_LIMITS) {
            return backInLimits(at);
        }
        final boolean escalated = observed.compareTo(level) > 0;
        final var worst = escalated ? observed : level;
        return switch (state) {
            case TRIGGERED -> with(worst, AlarmState.TRIGGERED, returnedAt, acknowledgedAt, clearedAt);
            case ACKNOWLEDGED -> escalated
                    ? with(worst, AlarmState.TRIGGERED, returnedAt, null, clearedAt)
                    : this;
            case RETURNED -> with(worst, AlarmState.TRIGGERED, null, acknowledgedAt, clearedAt);
            case CLEARED -> this;
        };
    }

    Alarm acknowledge(Instant at) {
        return switch (state) {
            case TRIGGERED -> with(level, AlarmState.ACKNOWLEDGED, returnedAt, at, clearedAt);
            case ACKNOWLEDGED -> this;
            case RETURNED -> with(level, AlarmState.CLEARED, returnedAt, at, at);
            case CLEARED -> throw new AlarmClearedException(id);
        };
    }

    private Alarm backInLimits(Instant at) {
        return switch (state) {
            case TRIGGERED -> with(level, AlarmState.RETURNED, at, acknowledgedAt, clearedAt);
            case ACKNOWLEDGED -> with(level, AlarmState.CLEARED, at, acknowledgedAt, at);
            case RETURNED, CLEARED -> this;
        };
    }

    private Alarm with(MonitoringLevel newLevel, AlarmState newState, Instant newReturnedAt,
                       Instant newAcknowledgedAt, Instant newClearedAt) {
        return new Alarm(id, version, noradId, kind, parameter, newLevel, newState, triggeredAt, triggerValue,
                newReturnedAt, newAcknowledgedAt, newClearedAt);
    }
}
