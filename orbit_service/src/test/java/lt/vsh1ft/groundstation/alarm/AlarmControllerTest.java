package lt.vsh1ft.groundstation.alarm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import lt.vsh1ft.xtce.MonitoringLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(AlarmController.class)
class AlarmControllerTest {

    private static final NoradId SATELLITE = new NoradId(99999);
    private static final AlarmResponse RETURNED = new AlarmResponse(3, "BATTERY_VOLTAGE", MonitoringLevel.CRITICAL,
            AlarmState.RETURNED, Instant.parse("2026-11-08T09:00:01Z"), 7.199, Instant.parse("2026-11-08T09:04:30Z"),
            null, null);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AlarmService service;

    @Test
    @Requirement("ALM-050")
    void openAlarmsAreListed() {
        given(service.open(SATELLITE)).willReturn(new AlarmsResponse(List.of(RETURNED)));

        assertThat(mvc.get().uri("/satellites/99999/alarms")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {"alarms": [{"id": 3, "parameter": "BATTERY_VOLTAGE", "level": "CRITICAL", "state": "RETURNED",
                             "triggeredAt": "2026-11-08T09:00:01Z", "triggerValue": 7.199,
                             "returnedAt": "2026-11-08T09:04:30Z", "acknowledgedAt": null, "clearedAt": null}]}
                """);
    }

    @Test
    @Requirement("ALM-050")
    void acknowledgementReturnsTheAlarmAfterwards() {
        final var cleared = new AlarmResponse(3, "BATTERY_VOLTAGE", MonitoringLevel.CRITICAL, AlarmState.CLEARED,
                RETURNED.triggeredAt(), 7.199, RETURNED.returnedAt(), Instant.parse("2026-11-08T09:30:00Z"),
                Instant.parse("2026-11-08T09:30:00Z"));
        given(service.acknowledge(SATELLITE, 3)).willReturn(cleared);

        assertThat(mvc.post().uri("/satellites/99999/alarms/3/acknowledge")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("""
                        {"id": 3, "state": "CLEARED", "acknowledgedAt": "2026-11-08T09:30:00Z",
                         "clearedAt": "2026-11-08T09:30:00Z"}
                        """);
    }

    @Test
    @Requirement({"ALM-040", "API-010"})
    void acknowledgingAClearedAlarmIsAConflictAndAnUnknownOneIsNotFound() {
        given(service.acknowledge(SATELLITE, 3)).willThrow(new AlarmClearedException(3));
        given(service.acknowledge(SATELLITE, 4)).willThrow(new UnknownAlarmException(SATELLITE, 4));

        assertThat(mvc.post().uri("/satellites/99999/alarms/3/acknowledge")).hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON).bodyJson()
                .isLenientlyEqualTo("{\"detail\": \"Alarm 3 is already cleared\"}");
        assertThat(mvc.post().uri("/satellites/99999/alarms/4/acknowledge")).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().isLenientlyEqualTo("{\"detail\": \"No alarm 4 for satellite 99999\"}");
    }

    @Test
    @Requirement("API-010")
    void invalidAlarmIdIsABadRequest() {
        assertThat(mvc.post().uri("/satellites/99999/alarms/0/acknowledge")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().isLenientlyEqualTo("""
                        {"errors": [{"field": "id", "message": "must be greater than 0"}]}
                        """);
        verifyNoInteractions(service);
    }
}
