package lt.vsh1ft.xtce;

import java.util.Optional;

public record ParameterValue(Parameter parameter, long raw, Optional<EngineeringValue> engineering) {
}
