package lt.vsh1ft.ccsds;

final class Fields {

    private Fields() {
    }

    static void requireRange(String name, long value, long min, long max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ", was " + value);
        }
    }
}
