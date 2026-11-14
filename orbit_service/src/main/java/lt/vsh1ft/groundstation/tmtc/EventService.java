package lt.vsh1ft.groundstation.tmtc;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.xtce.MissionDatabase;
import lt.vsh1ft.xtce.Parameter;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class EventService {

    private final EventArchive events;
    private final ParameterArchive parameters;
    private final MissionDatabases databases;

    EventsResponse events(NoradId satellite, EventsQuery query) {
        final var page = events.events(satellite, query);
        final var database = databases.of(satellite);
        final Map<Long, List<StoredParameterValue>> valuesByPacket = database.isEmpty() || page.items().isEmpty()
                ? Map.of()
                : parameters.ofPackets(satellite, page.items().stream().map(StoredEvent::packetId).toList())
                        .stream().collect(Collectors.groupingBy(StoredParameterValue::packetId));
        return new EventsResponse(page.items().stream()
                .map(event -> event(event, auxiliary(event, database), valuesByPacket))
                .toList(), page.nextBefore());
    }

    private static Map<String, Parameter> auxiliary(StoredEvent event, Optional<MissionDatabase> database) {
        return database.flatMap(db -> Optional.ofNullable(event.container()).flatMap(db::container))
                .map(container -> container.parameters().stream()
                        .collect(Collectors.toMap(Parameter::name, parameter -> parameter)))
                .orElse(Map.of());
    }

    private static EventsResponse.Event event(StoredEvent event, Map<String, Parameter> auxiliary,
                                              Map<Long, List<StoredParameterValue>> valuesByPacket) {
        final var values = valuesByPacket.getOrDefault(event.packetId(), List.of()).stream()
                .filter(value -> auxiliary.containsKey(value.parameter()))
                .map(value -> new EventsResponse.Value(value.parameter(), value.raw(),
                        value.engineering(auxiliary.get(value.parameter()).type())))
                .toList();
        return new EventsResponse.Event(event.id(), event.severity(), event.eventId(), event.name(),
                event.description(), event.generationTime(), event.packetId(), values);
    }
}
