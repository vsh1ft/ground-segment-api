package lt.vsh1ft.xtce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AlarmRangesTest {

    private static final AlarmRanges BATTERY = new AlarmRanges(new AlarmRange(7.20, true, 7.80, true),
            new AlarmRange(7.12, true, 7.90, true));

    @ParameterizedTest
    @CsvSource({
            "7.50, IN_LIMITS",
            "7.20, IN_LIMITS",
            "7.80, IN_LIMITS",
            "7.199, WARNING",
            "7.12, WARNING",
            "7.85, WARNING",
            "7.119, CRITICAL",
            "7.901, CRITICAL"
    })
    void valueOutsideTheWarningRangeIsAWarningAndOutsideTheCriticalRangeIsCritical(double volts,
                                                                                  MonitoringLevel expected) {
        assertThat(BATTERY.check(volts)).isEqualTo(expected);
    }

    @Test
    void exclusiveBoundsLeaveTheBoundItselfOutside() {
        final var range = new AlarmRange(0, false, 10, false);

        assertThat(range.contains(0)).isFalse();
        assertThat(range.contains(10)).isFalse();
        assertThat(range.contains(5)).isTrue();
    }

    @Test
    void withoutLimitsEveryValueIsInLimits() {
        assertThat(AlarmRanges.NONE.check(Double.MAX_VALUE)).isEqualTo(MonitoringLevel.IN_LIMITS);
        assertThat(AlarmRanges.NONE.check(-Double.MAX_VALUE)).isEqualTo(MonitoringLevel.IN_LIMITS);
    }

    @Test
    void sideLimitedOnlyByTheCriticalRangeGoesStraightToCritical() {
        final var upperOnly = new AlarmRanges(new AlarmRange(Double.NEGATIVE_INFINITY, true, 32.0, true),
                new AlarmRange(-20.0, true, 34.5, true));

        assertThat(upperOnly.check(-25.0)).isEqualTo(MonitoringLevel.CRITICAL);
        assertThat(upperOnly.check(33.0)).isEqualTo(MonitoringLevel.WARNING);
    }

    @Test
    void warningRangeAloneNeverGivesCritical() {
        final var warningOnly = new AlarmRanges(new AlarmRange(0, true, 9, true), AlarmRange.UNBOUNDED);

        assertThat(warningOnly.check(-1000)).isEqualTo(MonitoringLevel.WARNING);
        assertThat(warningOnly.check(1000)).isEqualTo(MonitoringLevel.WARNING);
    }

    @Test
    void rangeWithItsMinimumAboveItsMaximumIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AlarmRange(2, true, 1, true))
                .withMessage("minimum 2.0 is above maximum 1.0");
    }

    @Test
    void warningLimitOutsideTheCriticalLimitIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AlarmRanges(new AlarmRange(7.0, true, 7.8, true),
                        new AlarmRange(7.12, true, 7.9, true)))
                .withMessage("the warning minimum 7.0 lies outside the critical minimum 7.12");
        assertThatIllegalArgumentException().isThrownBy(() -> new AlarmRanges(new AlarmRange(7.2, true, 8.0, true),
                        new AlarmRange(7.12, true, 7.9, true)))
                .withMessage("the warning maximum 8.0 lies outside the critical maximum 7.9");
    }

    @Test
    void equalLimitsNestOnlyWhenTheWarningBoundIsNotWiderThanTheCriticalBound() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AlarmRanges(new AlarmRange(0, true, 5, true),
                new AlarmRange(0, false, 9, true)));
        assertThatIllegalArgumentException().isThrownBy(() -> new AlarmRanges(new AlarmRange(1, true, 9, true),
                new AlarmRange(0, true, 9, false)));
        assertThat(new AlarmRanges(new AlarmRange(0, true, 9, true), new AlarmRange(0, true, 9, true))
                .check(9)).isEqualTo(MonitoringLevel.IN_LIMITS);
        assertThat(new AlarmRanges(new AlarmRange(0, false, 9, false), new AlarmRange(0, false, 9, false))
                .check(9)).isEqualTo(MonitoringLevel.CRITICAL);
    }

    @Test
    void bothLevelsAreRequiredEvenIfUnbounded() {
        assertThatNullPointerException().isThrownBy(() -> new AlarmRanges(null, AlarmRange.UNBOUNDED));
        assertThatNullPointerException().isThrownBy(() -> new AlarmRanges(AlarmRange.UNBOUNDED, null));
    }
}
