package lt.vsh1ft.xtce;

import java.util.Optional;

public record StringValue(String text) implements EngineeringValue {

    @Override
    public Optional<Double> number() {
        return Optional.empty();
    }
}
