package lt.vsh1ft.groundstation.tmtc;

import static lt.vsh1ft.groundstation.tmtc.SimulatorTelemetry.SATELLITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;

class EventServiceTest {

    private static final Instant GENERATED = Instant.parse("2026-11-08T09:00:00Z");
    private static final EventsQuery DEFAULTS = new EventsQuery(null, null, null, null, null);
    private static final StoredEvent BATTERY_LOW = new StoredEvent(7L, SATELLITE.value(), 40L, Severity.LOW, 1,
            "BATTERY_LOW", "Battery voltage below 7.15 V", "EVENT_BATTERY_LOW", GENERATED);
    private static final StoredEvent UNKNOWN = new StoredEvent(8L, SATELLITE.value(), 41L, Severity.HIGH, 99, null,
            null, null, GENERATED);
    private static final StoredEvent OUTDATED = new StoredEvent(9L, SATELLITE.value(), 42L, Severity.INFO, 7,
            "SOLAR_FLARE", null, "EVENT_SOLAR_FLARE", GENERATED);

    private final EventArchive events = mock(EventArchive.class);
    private final ParameterArchive parameters = mock(ParameterArchive.class);
    private final EventService service = new EventService(events, parameters, SimulatorTelemetry.databases());

    @Test
    @Requirement("EVT-020")
    void eventsCarryOnlyTheAuxiliaryValuesOfTheirOwnContainer() {
        given(events.events(SATELLITE, DEFAULTS)).willReturn(new EventArchive.Page(
                List.of(BATTERY_LOW, UNKNOWN, OUTDATED), 7L));
        given(parameters.ofPackets(SATELLITE, List.of(40L, 41L, 42L))).willReturn(List.of(
                value(40L, "SERVICE_TYPE", 5, 5.0, null),
                value(40L, "EVENT_BATTERY_VOLTAGE", 7149, 7.149, null),
                value(41L, "EVENT_ID", 99, null, null)));

        final var response = service.events(SATELLITE, DEFAULTS);

        assertThat(response.nextBefore()).isEqualTo(7L);
        assertThat(response.events()).extracting(EventsResponse.Event::values).containsExactly(
                List.of(new EventsResponse.Value("EVENT_BATTERY_VOLTAGE", 7149, 7.149)), List.of(), List.of());
        assertThat(response.events().getFirst()).extracting(EventsResponse.Event::name, EventsResponse.Event::severity)
                .containsExactly("BATTERY_LOW", Severity.LOW);
    }

    @Test
    @Requirement("EVT-010")
    void eventsOfASatelliteWithoutAMissionDatabaseAreListedWithoutValues() {
        final var other = new NoradId(25544);
        final var event = new StoredEvent(1L, 25544, 5L, Severity.MEDIUM, null, null, null, null, GENERATED);
        given(events.events(other, DEFAULTS)).willReturn(new EventArchive.Page(List.of(event), null));

        assertThat(service.events(other, DEFAULTS).events()).singleElement()
                .extracting(EventsResponse.Event::values).isEqualTo(List.of());
        verifyNoInteractions(parameters);
    }

    @Test
    void emptyLogNeedsNoValues() {
        given(events.events(SATELLITE, DEFAULTS)).willReturn(new EventArchive.Page(List.of(), null));

        assertThat(service.events(SATELLITE, DEFAULTS).events()).isEmpty();
        verifyNoInteractions(parameters);
    }

    private static StoredParameterValue value(long packetId, String parameter, long raw, Double number, String text) {
        return new StoredParameterValue(null, SATELLITE.value(), packetId, null, parameter, GENERATED, raw, number,
                text);
    }
}
