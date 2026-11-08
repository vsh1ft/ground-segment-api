package lt.vsh1ft.xtce;

public record Parameter(String name, String description, ParameterType type) {

    ParameterValue decode(BitReader reader) {
        final long raw = type.encoding().decode(reader);
        return new ParameterValue(this, raw, type.calibrate(raw));
    }
}
