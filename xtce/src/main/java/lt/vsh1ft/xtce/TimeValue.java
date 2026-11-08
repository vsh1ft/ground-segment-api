package lt.vsh1ft.xtce;

import java.time.Instant;
import java.util.Optional;

public record TimeValue(Instant time) implements EngineeringValue {

    @Override
    public Optional<Double> number() {
        return Optional.empty();
    }
}
