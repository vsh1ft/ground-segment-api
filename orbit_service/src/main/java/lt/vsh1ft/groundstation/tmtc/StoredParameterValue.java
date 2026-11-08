package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;
import java.util.Optional;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.xtce.AbsoluteTimeParameterType;
import lt.vsh1ft.xtce.EngineeringValue;
import lt.vsh1ft.xtce.EnumeratedParameterType;
import lt.vsh1ft.xtce.EnumeratedValue;
import lt.vsh1ft.xtce.FloatParameterType;
import lt.vsh1ft.xtce.FloatValue;
import lt.vsh1ft.xtce.IntegerParameterType;
import lt.vsh1ft.xtce.IntegerValue;
import lt.vsh1ft.xtce.ParameterType;
import lt.vsh1ft.xtce.ParameterValue;
import lt.vsh1ft.xtce.StringParameterType;
import lt.vsh1ft.xtce.StringValue;
import lt.vsh1ft.xtce.TimeValue;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("parameter_value")
record StoredParameterValue(
        @Id Long id,
        int noradId,
        Long packetId,
        Long frameId,
        String parameter,
        Instant generationTime,
        long raw,
        Double engineeringNumber,
        String engineeringText) {

    static StoredParameterValue of(NoradId satellite, long packetId, Instant generationTime, ParameterValue value) {
        return of(satellite, packetId, null, generationTime, value);
    }

    static StoredParameterValue ofFrame(NoradId satellite, long frameId, Instant generationTime,
                                        ParameterValue value) {
        return of(satellite, null, frameId, generationTime, value);
    }

    private static StoredParameterValue of(NoradId satellite, Long packetId, Long frameId, Instant generationTime,
                                           ParameterValue value) {
        final var engineering = value.engineering();
        return new StoredParameterValue(null, satellite.value(), packetId, frameId, value.parameter().name(),
                generationTime, value.raw(), engineering.flatMap(EngineeringValue::number).orElse(null),
                engineering.map(StoredParameterValue::text).orElse(null));
    }

    private static String text(EngineeringValue engineering) {
        return switch (engineering) {
            case EnumeratedValue(String label) -> label;
            case StringValue(String text) -> text;
            case TimeValue(Instant time) -> time.toString();
            case IntegerValue integer -> null;
            case FloatValue floating -> null;
        };
    }

    Object engineering(ParameterType type) {
        final var number = Optional.ofNullable(engineeringNumber);
        return switch (type) {
            case IntegerParameterType integer -> number.map(Double::longValue).orElse(null);
            case FloatParameterType floating -> number.orElse(null);
            case EnumeratedParameterType enumerated -> engineeringText;
            case StringParameterType string -> engineeringText;
            case AbsoluteTimeParameterType time -> engineeringText;
        };
    }
}
