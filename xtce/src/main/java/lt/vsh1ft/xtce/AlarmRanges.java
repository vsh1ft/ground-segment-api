package lt.vsh1ft.xtce;

import java.util.Objects;

public record AlarmRanges(AlarmRange warning, AlarmRange critical) {

    public static final AlarmRanges NONE = new AlarmRanges(AlarmRange.UNBOUNDED, AlarmRange.UNBOUNDED);

    public AlarmRanges {
        Objects.requireNonNull(warning, "warning");
        Objects.requireNonNull(critical, "critical");
        if (warning.hasMin() && critical.hasMin() && !lowerNested(warning, critical)) {
            throw new IllegalArgumentException("the warning minimum " + warning.min()
                    + " lies outside the critical minimum " + critical.min());
        }
        if (warning.hasMax() && critical.hasMax() && !upperNested(warning, critical)) {
            throw new IllegalArgumentException("the warning maximum " + warning.max()
                    + " lies outside the critical maximum " + critical.max());
        }
    }

    public MonitoringLevel check(double value) {
        if (!critical.contains(value)) {
            return MonitoringLevel.CRITICAL;
        }
        if (!warning.contains(value)) {
            return MonitoringLevel.WARNING;
        }
        return MonitoringLevel.IN_LIMITS;
    }

    private static boolean lowerNested(AlarmRange warning, AlarmRange critical) {
        return warning.min() > critical.min()
                || warning.min() == critical.min() && (critical.minInclusive() || !warning.minInclusive());
    }

    private static boolean upperNested(AlarmRange warning, AlarmRange critical) {
        return warning.max() < critical.max()
                || warning.max() == critical.max() && (critical.maxInclusive() || !warning.maxInclusive());
    }
}
