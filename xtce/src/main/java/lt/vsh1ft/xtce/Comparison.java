package lt.vsh1ft.xtce;

import java.util.Map;
import java.util.Optional;

public record Comparison(Parameter parameter, EngineeringValue expected, boolean calibrated) {

    boolean matches(Map<String, ParameterValue> decoded) {
        final var value = decoded.get(parameter.name());
        final Optional<EngineeringValue> actual = calibrated
                ? value.engineering()
                : Optional.of(new IntegerValue(value.raw()));
        return actual.equals(Optional.of(expected));
    }
}
