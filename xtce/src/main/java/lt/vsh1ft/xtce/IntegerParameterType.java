package lt.vsh1ft.xtce;

import java.util.Optional;

public record IntegerParameterType(String name, String unit, IntegerEncoding encoding) implements ParameterType {

    @Override
    public Optional<EngineeringValue> calibrate(long raw) {
        return Optional.of(new IntegerValue(raw));
    }

    @Override
    public EngineeringValue parse(String literal) {
        return new IntegerValue(Long.parseLong(literal));
    }
}
