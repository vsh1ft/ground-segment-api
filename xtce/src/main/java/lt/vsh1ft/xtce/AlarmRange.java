package lt.vsh1ft.xtce;

public record AlarmRange(double min, boolean minInclusive, double max, boolean maxInclusive) {

    public static final AlarmRange UNBOUNDED = new AlarmRange(Double.NEGATIVE_INFINITY, true,
            Double.POSITIVE_INFINITY, true);

    public AlarmRange {
        if (min > max) {
            throw new IllegalArgumentException("minimum " + min + " is above maximum " + max);
        }
    }

    public boolean contains(double value) {
        final boolean aboveMin = minInclusive ? value >= min : value > min;
        final boolean belowMax = maxInclusive ? value <= max : value < max;
        return aboveMin && belowMax;
    }

    boolean hasMin() {
        return min != Double.NEGATIVE_INFINITY;
    }

    boolean hasMax() {
        return max != Double.POSITIVE_INFINITY;
    }
}
