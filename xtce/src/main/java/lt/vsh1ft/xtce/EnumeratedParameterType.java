package lt.vsh1ft.xtce;

import java.util.Map;
import java.util.Optional;

public record EnumeratedParameterType(String name, IntegerEncoding encoding, Map<Long, String> labels,
                                      Map<Long, String> descriptions) implements ParameterType {

    public EnumeratedParameterType {
        labels = Map.copyOf(labels);
        descriptions = Map.copyOf(descriptions);
    }

    @Override
    public String unit() {
        return null;
    }

    @Override
    public Optional<EngineeringValue> calibrate(long raw) {
        return Optional.ofNullable(labels.get(raw)).map(EnumeratedValue::new);
    }

    @Override
    public EngineeringValue parse(String literal) {
        if (!labels.containsValue(literal)) {
            throw new IllegalArgumentException("not a label of " + name);
        }
        return new EnumeratedValue(literal);
    }

    public Optional<String> description(long raw) {
        return Optional.ofNullable(descriptions.get(raw));
    }
}
