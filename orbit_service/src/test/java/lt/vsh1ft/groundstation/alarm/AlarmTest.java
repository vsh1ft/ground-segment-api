package lt.vsh1ft.groundstation.alarm;

import static lt.vsh1ft.xtce.MonitoringLevel.CRITICAL;
import static lt.vsh1ft.xtce.MonitoringLevel.IN_LIMITS;
import static lt.vsh1ft.xtce.MonitoringLevel.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;

class AlarmTest {

    private static final NoradId SATELLITE = new NoradId(99999);
    private static final Instant T0 = Instant.parse("2026-11-08T09:00:01Z");
    private static final Instant T1 = T0.plusSeconds(69);
    private static final Instant T2 = T0.plusSeconds(159);
    private static final Instant T3 = T0.plusSeconds(269);
    private static final Instant ACK = Instant.parse("2026-11-08T09:30:00Z");

    private final Alarm warning = Alarm.raise(SATELLITE, "BATTERY_VOLTAGE", WARNING, T0, 7.199);

    @Test
    @Requirement("ALM-030")
    void raisedAlarmIsTriggeredWithItsTriggerValue() {
        assertThat(warning.state()).isEqualTo(AlarmState.TRIGGERED);
        assertThat(warning.level()).isEqualTo(WARNING);
        assertThat(warning.triggeredAt()).isEqualTo(T0);
        assertThat(warning.triggerValue()).isEqualTo(7.199);
        assertThat(warning.noradId()).isEqualTo(99999);
    }

    @Test
    @Requirement({"ALM-030", "ALM-040"})
    void batteryDipFromTheTutorialEndsClearedAndKeepsItsWorstLevel() {
        final var unchanged = warning.observe(WARNING, T1);
        final var acknowledged = unchanged.acknowledge(T1);
        final var escalated = acknowledged.observe(CRITICAL, T2);
        final var improving = escalated.observe(WARNING, T2);
        final var returned = improving.observe(IN_LIMITS, T3);
        final var cleared = returned.acknowledge(ACK);

        assertThat(unchanged).isEqualTo(warning);
        assertThat(acknowledged.state()).isEqualTo(AlarmState.ACKNOWLEDGED);
        assertThat(acknowledged.acknowledgedAt()).isEqualTo(T1);
        assertThat(escalated.state()).isEqualTo(AlarmState.TRIGGERED);
        assertThat(escalated.level()).isEqualTo(CRITICAL);
        assertThat(escalated.acknowledgedAt()).isNull();
        assertThat(improving.level()).isEqualTo(CRITICAL);
        assertThat(returned.state()).isEqualTo(AlarmState.RETURNED);
        assertThat(returned.returnedAt()).isEqualTo(T3);
        assertThat(cleared.state()).isEqualTo(AlarmState.CLEARED);
        assertThat(cleared.acknowledgedAt()).isEqualTo(ACK);
        assertThat(cleared.clearedAt()).isEqualTo(ACK);
        assertThat(cleared.triggeredAt()).isEqualTo(T0);
    }

    @Test
    @Requirement("ALM-040")
    void acknowledgedAlarmClearsWhenItsValueReturns() {
        final var cleared = warning.acknowledge(T1).observe(IN_LIMITS, T2);

        assertThat(cleared.state()).isEqualTo(AlarmState.CLEARED);
        assertThat(cleared.returnedAt()).isEqualTo(T2);
        assertThat(cleared.clearedAt()).isEqualTo(T2);
        assertThat(cleared.acknowledgedAt()).isEqualTo(T1);
    }

    @Test
    @Requirement("ALM-040")
    void acknowledgedAlarmStaysAcknowledgedWhileItsValueStaysAtTheSameLevel() {
        final var acknowledged = warning.acknowledge(T1);

        assertThat(acknowledged.observe(WARNING, T2)).isEqualTo(acknowledged);
        assertThat(acknowledged.acknowledge(T2)).isEqualTo(acknowledged);
    }

    @Test
    @Requirement("ALM-030")
    void returnedAlarmIsTriggeredAgainWhenItsValueLeavesTheLimitsAgain() {
        final var returned = warning.observe(IN_LIMITS, T1);
        final var again = returned.observe(WARNING, T2);

        assertThat(returned.observe(IN_LIMITS, T2)).isEqualTo(returned);
        assertThat(again.state()).isEqualTo(AlarmState.TRIGGERED);
        assertThat(again.returnedAt()).isNull();
        assertThat(again.level()).isEqualTo(WARNING);
    }

    @Test
    @Requirement("ALM-040")
    void clearedAlarmIgnoresNewValuesAndCannotBeAcknowledged() {
        final var cleared = new Alarm(3L, 2L, 99999, AlarmKind.LIMIT, "BATTERY_VOLTAGE", WARNING, AlarmState.CLEARED, T0,
                7.199, T1, T1, T1);

        assertThat(cleared.observe(CRITICAL, T2)).isEqualTo(cleared);
        assertThat(cleared.observe(IN_LIMITS, T2)).isEqualTo(cleared);
        assertThatThrownBy(() -> cleared.acknowledge(T2)).isInstanceOf(AlarmClearedException.class)
                .hasMessage("Alarm 3 is already cleared");
    }

    @Test
    @Requirement("ALM-070")
    void limitAlarmHasTheKindLimit() {
        assertThat(warning.kind()).isEqualTo(AlarmKind.LIMIT);
    }

    @Test
    @Requirement("ALM-070")
    void lossOfSignalIsATriggeredWarningWithoutParameterOrValue() {
        final var lost = Alarm.onLink(AlarmKind.LOSS_OF_SIGNAL, SATELLITE, T0);

        assertThat(lost.kind()).isEqualTo(AlarmKind.LOSS_OF_SIGNAL);
        assertThat(lost.level()).isEqualTo(WARNING);
        assertThat(lost.state()).isEqualTo(AlarmState.TRIGGERED);
        assertThat(lost.triggeredAt()).isEqualTo(T0);
        assertThat(lost.parameter()).isNull();
        assertThat(lost.triggerValue()).isNull();
    }

    @Test
    @Requirement("ALM-070")
    void lossOfSignalFollowsTheSameLifecycle() {
        final var returned = Alarm.onLink(AlarmKind.LOSS_OF_SIGNAL, SATELLITE, T0).observe(IN_LIMITS, T1);
        final var again = returned.observe(WARNING, T2);

        assertThat(returned.state()).isEqualTo(AlarmState.RETURNED);
        assertThat(again.state()).isEqualTo(AlarmState.TRIGGERED);
        assertThat(again.acknowledge(ACK).observe(IN_LIMITS, T3).state()).isEqualTo(AlarmState.CLEARED);
    }

    @Test
    @Requirement("ALM-080")
    void linkQualityAlarmIsATriggeredWarningWithoutParameterOrValue() {
        final var degraded = Alarm.onLink(AlarmKind.LINK_QUALITY, SATELLITE, T0);

        assertThat(degraded.kind()).isEqualTo(AlarmKind.LINK_QUALITY);
        assertThat(degraded.level()).isEqualTo(WARNING);
        assertThat(degraded.state()).isEqualTo(AlarmState.TRIGGERED);
        assertThat(degraded.parameter()).isNull();
        assertThat(degraded.triggerValue()).isNull();
    }

    @Test
    void onlyALimitAlarmHasAParameterAndATriggerValue() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Alarm(null, null, 99999, AlarmKind.LIMIT, null,
                WARNING, AlarmState.TRIGGERED, T0, 7.199, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Alarm(null, null, 99999, AlarmKind.LIMIT,
                "BATTERY_VOLTAGE", WARNING, AlarmState.TRIGGERED, T0, null, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Alarm(null, null, 99999,
                AlarmKind.LOSS_OF_SIGNAL, "BATTERY_VOLTAGE", WARNING, AlarmState.TRIGGERED, T0, 7.199, null, null,
                null)).withMessage("Only a limit alarm has a parameter and a trigger value: LOSS_OF_SIGNAL, "
                + "BATTERY_VOLTAGE, 7.199");
    }

    @Test
    void alarmIsNeverInLimits() {
        assertThatIllegalArgumentException().isThrownBy(() -> Alarm.raise(SATELLITE, "BATTERY_VOLTAGE", IN_LIMITS,
                T0, 7.5));
    }
}
