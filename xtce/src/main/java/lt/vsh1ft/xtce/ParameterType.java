package lt.vsh1ft.xtce;

import java.util.Optional;

public sealed interface ParameterType permits IntegerParameterType, FloatParameterType, EnumeratedParameterType,
        StringParameterType, AbsoluteTimeParameterType {

    String name();

    String unit();

    IntegerEncoding encoding();

    Optional<EngineeringValue> calibrate(long raw);

    EngineeringValue parse(String literal);

    MonitoringLevel monitor(long raw);
}
