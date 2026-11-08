package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;
import java.util.Optional;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.xtce.DecodedPacket;
import lt.vsh1ft.xtce.EnumeratedParameterType;
import lt.vsh1ft.xtce.EnumeratedValue;
import lt.vsh1ft.xtce.ParameterValue;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("event")
record StoredEvent(
        @Id Long id,
        int noradId,
        long packetId,
        Severity severity,
        Integer eventId,
        String name,
        String description,
        String container,
        Instant generationTime) {

    static final String EVENT_ID = "EVENT_ID";

    static StoredEvent of(NoradId satellite, long packetId, Instant generationTime, Severity severity,
                          Optional<DecodedPacket> decoded) {
        final var id = decoded.flatMap(StoredEvent::eventId);
        final var container = decoded.filter(DecodedPacket::complete).map(packet -> packet.container().name());
        return new StoredEvent(null, satellite.value(), packetId, severity,
                id.map(value -> (int) value.raw()).orElse(null),
                id.flatMap(StoredEvent::label).orElse(null),
                id.flatMap(StoredEvent::description).orElse(null),
                container.orElse(null), generationTime);
    }

    private static Optional<ParameterValue> eventId(DecodedPacket decoded) {
        return decoded.values().stream().filter(value -> value.parameter().name().equals(EVENT_ID)).findFirst();
    }

    private static Optional<String> label(ParameterValue id) {
        return id.engineering().filter(EnumeratedValue.class::isInstance).map(EnumeratedValue.class::cast)
                .map(EnumeratedValue::label);
    }

    private static Optional<String> description(ParameterValue id) {
        return Optional.of(id.parameter().type()).filter(EnumeratedParameterType.class::isInstance)
                .map(EnumeratedParameterType.class::cast).flatMap(type -> type.description(id.raw()));
    }
}
