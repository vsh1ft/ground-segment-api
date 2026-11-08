package lt.vsh1ft.groundstation.tmtc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(TelemetryController.class)
class TelemetryControllerTest {

    private static final NoradId SATELLITE = new NoradId(99999);
    private static final TmPacketResponse HOUSEKEEPING = new TmPacketResponse(42, 100, 5, 3, 25,
            Instant.parse("2026-11-08T00:00:00.5Z"), Instant.parse("2026-11-08T00:00:00.512Z"),
            "0864C00500122003190001000012345678800001020304FB2C");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TmPacketArchive archive;

    @Test
    @Requirement({"TM-040", "TM-050"})
    void packetsOfTheSatelliteAreListedWithEveryFilterAndTheCursorForTheNextPage() {
        final var query = new PacketsQuery(1, 50L, Instant.parse("2026-11-08T00:00:00Z"),
                Instant.parse("2026-11-08T01:00:00Z"), 100, 3, 25);
        given(archive.packets(SATELLITE, query)).willReturn(new TmPacketsResponse(List.of(HOUSEKEEPING), 42L));

        assertThat(mvc.get().uri("/satellites/99999/telemetry/packets?limit=1&before=50"
                + "&from=2026-11-08T00:00:00Z&to=2026-11-08T01:00:00Z&apid=100&service=3&subtype=25"))
                .hasStatusOk().bodyJson().isStrictlyEqualTo("""
                        {"packets": [{"id": 42, "apid": 100, "sequenceCount": 5, "serviceType": 3,
                                      "messageSubtype": 25, "generationTime": "2026-11-08T00:00:00.500Z",
                                      "receptionTime": "2026-11-08T00:00:00.512Z",
                                      "raw": "0864C00500122003190001000012345678800001020304FB2C"}],
                         "nextBefore": 42}
                        """);
    }

    @Test
    @Requirement("TM-040")
    void packetsDefaultToTheNewestTwentyWithoutFilters() {
        given(archive.packets(SATELLITE, new PacketsQuery(20, null, null, null, null, null, null)))
                .willReturn(new TmPacketsResponse(List.of(), null));

        assertThat(mvc.get().uri("/satellites/99999/telemetry/packets")).hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"packets": [], "nextBefore": null}
                        """);
    }

    @Test
    @Requirement({"TM-050", "API-010"})
    void invalidParametersAreABadRequestNamingEveryOne() {
        final var response = mvc.get().uri("/satellites/99999/telemetry/packets"
                + "?limit=101&before=0&apid=2048&service=256&subtype=-1").exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().isLenientlyEqualTo("""
                        {"errors": [{"field": "apid", "message": "must be less than or equal to 2047"},
                                    {"field": "before", "message": "must be greater than 0"},
                                    {"field": "limit", "message": "must be less than or equal to 100"},
                                    {"field": "service", "message": "must be less than or equal to 255"},
                                    {"field": "subtype", "message": "must be greater than or equal to 0"}]}
                        """);
        verifyNoInteractions(archive);
    }

    @Test
    @Requirement("API-010")
    void nonPositiveNoradIdIsABadRequest() {
        assertThat(mvc.get().uri("/satellites/0/telemetry/packets")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(archive);
    }
}
