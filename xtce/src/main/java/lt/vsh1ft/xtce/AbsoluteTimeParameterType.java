package lt.vsh1ft.xtce;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record AbsoluteTimeParameterType(String name, IntegerEncoding encoding, Instant epoch, BigDecimal scale,
                                        BigDecimal offset) implements ParameterType {

    private static final BigDecimal NANOS_PER_SECOND = BigDecimal.valueOf(1_000_000_000L);

    public AbsoluteTimeParameterType {
        Objects.requireNonNull(epoch, "epoch");
        Objects.requireNonNull(scale, "scale");
        Objects.requireNonNull(offset, "offset");
    }

    @Override
    public String unit() {
        return null;
    }

    @Override
    public Optional<EngineeringValue> calibrate(long raw) {
        final var seconds = BigDecimal.valueOf(raw).multiply(scale).add(offset);
        final var nanos = seconds.multiply(NANOS_PER_SECOND).setScale(0, RoundingMode.HALF_UP).longValueExact();
        return Optional.of(new TimeValue(epoch.plusNanos(nanos)));
    }

    @Override
    public EngineeringValue parse(String literal) {
        return new TimeValue(Instant.parse(literal));
    }

    @Override
    public MonitoringLevel monitor(long raw) {
        return MonitoringLevel.IN_LIMITS;
    }
}
