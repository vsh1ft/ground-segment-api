package lt.vsh1ft.xtce;

import java.util.Optional;

public record FloatParameterType(String name, String unit, IntegerEncoding encoding, PolynomialCalibrator calibrator,
                                 AlarmRanges limits) implements ParameterType {

    @Override
    public Optional<EngineeringValue> calibrate(long raw) {
        return Optional.of(new FloatValue(calibrator.apply(raw)));
    }

    @Override
    public EngineeringValue parse(String literal) {
        return new FloatValue(Double.parseDouble(literal));
    }

    @Override
    public MonitoringLevel monitor(long raw) {
        return limits.check(calibrator.apply(raw));
    }
}
