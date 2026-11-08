package lt.vsh1ft.xtce;

import java.util.Optional;

public record FloatValue(double value) implements EngineeringValue {

    @Override
    public Optional<Double> number() {
        return Optional.of(value);
    }
}
