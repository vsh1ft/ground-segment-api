package lt.vsh1ft.xtce;

import java.util.Optional;

public sealed interface EngineeringValue permits IntegerValue, FloatValue, EnumeratedValue, StringValue, TimeValue {

    Optional<Double> number();
}
