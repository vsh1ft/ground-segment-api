package lt.vsh1ft.xtce;

import java.util.Optional;

public record IntegerValue(long value) implements EngineeringValue {

    @Override
    public Optional<Double> number() {
        return Optional.of((double) value);
    }
}
