package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.xtce.EngineeringValue;
import lt.vsh1ft.xtce.EnumeratedValue;
import lt.vsh1ft.xtce.FloatValue;
import lt.vsh1ft.xtce.IntegerValue;
import lt.vsh1ft.xtce.ParameterValue;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("parameter_value")
record StoredParameterValue(
        @Id Long id,
        int noradId,
        long packetId,
        String parameter,
        Instant generationTime,
        long raw,
        Double engineeringNumber,
        String engineeringText) {

    static StoredParameterValue of(NoradId satellite, long packetId, Instant generationTime, ParameterValue value) {
        final var engineering = value.engineering().orElse(null);
        return new StoredParameterValue(null, satellite.value(), packetId, value.parameter().name(), generationTime,
                value.raw(), number(engineering), engineering instanceof EnumeratedValue(String label1) ? label1 : null);
    }

    private static Double number(EngineeringValue engineering) {
        return switch (engineering) {
            case IntegerValue integer -> (double) integer.value();
            case FloatValue floating -> floating.value();
            case null, default -> null;
        };
    }
}
