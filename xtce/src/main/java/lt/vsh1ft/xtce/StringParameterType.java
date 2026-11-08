package lt.vsh1ft.xtce;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public record StringParameterType(String name, IntegerEncoding encoding) implements ParameterType {

    public StringParameterType {
        if (encoding.signed() || encoding.littleEndian() || encoding.sizeInBits() % Byte.SIZE != 0) {
            throw new IllegalArgumentException("a string is a whole number of unsigned bytes in order, was "
                    + encoding);
        }
    }

    @Override
    public String unit() {
        return null;
    }

    @Override
    public Optional<EngineeringValue> calibrate(long raw) {
        final var length = encoding.sizeInBits() / Byte.SIZE;
        final var bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (raw >>> (Byte.SIZE * (length - 1 - i)));
        }
        var end = 0;
        while (end < length && bytes[end] != 0) {
            end++;
        }
        return Optional.of(new StringValue(new String(bytes, 0, end, StandardCharsets.US_ASCII)));
    }

    @Override
    public EngineeringValue parse(String literal) {
        return new StringValue(literal);
    }

    @Override
    public MonitoringLevel monitor(long raw) {
        return MonitoringLevel.IN_LIMITS;
    }
}
