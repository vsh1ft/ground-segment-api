package lt.vsh1ft.groundstation.shared;

public record NoradId(int value) {

    public NoradId {
        if (value <= 0) {
            throw new IllegalArgumentException("NORAD ID must be positive: " + value);
        }
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
