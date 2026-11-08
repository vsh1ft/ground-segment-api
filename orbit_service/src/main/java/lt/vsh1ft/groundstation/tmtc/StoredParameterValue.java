package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;
import java.util.Optional;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.xtce.EngineeringValue;
import lt.vsh1ft.xtce.EnumeratedParameterType;
import lt.vsh1ft.xtce.EnumeratedValue;
import lt.vsh1ft.xtce.FloatParameterType;
import lt.vsh1ft.xtce.IntegerParameterType;
import lt.vsh1ft.xtce.ParameterType;
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
        final var engineering = value.engineering();
        return new StoredParameterValue(null, satellite.value(), packetId, value.parameter().name(), generationTime,
                value.raw(), engineering.flatMap(EngineeringValue::number).orElse(null),
                engineering.orElse(null) instanceof EnumeratedValue(String label) ? label : null);
    }

    Object engineering(ParameterType type) {
        final var number = Optional.ofNullable(engineeringNumber);
        return switch (type) {
            case IntegerParameterType integer -> number.map(Double::longValue).orElse(null);
            case FloatParameterType floating -> number.orElse(null);
            case EnumeratedParameterType enumerated -> engineeringText;
        };
    }
}
