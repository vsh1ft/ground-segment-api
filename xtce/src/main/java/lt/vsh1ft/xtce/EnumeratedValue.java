package lt.vsh1ft.xtce;

import java.util.Optional;

public record EnumeratedValue(String label) implements EngineeringValue {

    @Override
    public Optional<Double> number() {
        return Optional.empty();
    }
}
