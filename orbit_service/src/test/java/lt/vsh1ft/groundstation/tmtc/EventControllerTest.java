package lt.vsh1ft.groundstation.tmtc;

import static lt.vsh1ft.groundstation.tmtc.SimulatorTelemetry.SATELLITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;

import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private EventService service;

    @Test
    @Requirement({"EVT-020", "EVT-030"})
    void eventsAreListedWithEveryFilterAndTheCursor() {
        final var query = new EventsQuery(1, 50L, Instant.parse("2026-11-08T08:00:00Z"),
                Instant.parse("2026-11-08T10:00:00Z"), Severity.LOW);
        given(service.events(SATELLITE, query)).willReturn(new EventsResponse(List.of(new EventsResponse.Event(7,
                Severity.LOW, 1, "BATTERY_LOW", "Battery voltage below 7.15 V", Instant.parse("2026-11-08T09:00:00Z"),
                40, List.of(new EventsResponse.Value("EVENT_BATTERY_VOLTAGE", 7149, 7.149)))), 7L));

        assertThat(mvc.get().uri("/satellites/99999/events?limit=1&before=50&from=2026-11-08T08:00:00Z"
                + "&to=2026-11-08T10:00:00Z&minSeverity=LOW"))
                .hasStatusOk().bodyJson().isStrictlyEqualTo("""
                        {"events": [{"id": 7, "severity": "LOW", "eventId": 1, "name": "BATTERY_LOW",
                                     "description": "Battery voltage below 7.15 V",
                                     "generationTime": "2026-11-08T09:00:00Z", "packetId": 40,
                                     "values": [{"parameter": "EVENT_BATTERY_VOLTAGE", "raw": 7149,
                                                 "engineering": 7.149}]}],
                         "nextBefore": 7}
                        """);
    }

    @Test
    @Requirement({"EVT-030", "API-010"})
    void invalidEventQueryIsABadRequest() {
        final var response = mvc.get().uri("/satellites/99999/events?limit=101").exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().isLenientlyEqualTo("""
                        {"errors": [{"field": "limit", "message": "must be less than or equal to 100"}]}
                        """);
        verifyNoInteractions(service);
    }
}
