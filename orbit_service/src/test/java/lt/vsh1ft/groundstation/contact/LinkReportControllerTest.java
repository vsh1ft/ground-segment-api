package lt.vsh1ft.groundstation.contact;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(LinkReportController.class)
class LinkReportControllerTest {

    private static final NoradId SATELLITE = new NoradId(99999);
    private static final LinkReport REPORT = new LinkReport(99999, Instant.parse("2026-11-08T18:02:27.7Z"),
            Instant.parse("2026-11-08T18:10:07.1Z"), 1712, 128, 41, 1301, Instant.parse("2026-11-08T18:10:07.1Z"));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private LinkReportArchive archive;

    @Test
    @Requirement("CON-050")
    void reportsAreListedWithTheirLossRatio() {
        given(archive.newestFirst(SATELLITE, 20)).willReturn(List.of(REPORT));

        assertThat(mvc.get().uri("/satellites/99999/link-reports")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {"reports": [{"passStart": "2026-11-08T18:02:27.700Z", "passEnd": "2026-11-08T18:10:07.100Z",
                              "framesReceived": 1712, "framesLost": 128, "framesFailedCheck": 41,
                              "idleFrames": 1301, "lossRatio": 0.06956521739130435,
                              "updatedAt": "2026-11-08T18:10:07.100Z"}]}
                """);
    }

    @Test
    @Requirement("CON-050")
    void limitOfOneHundredIsAcceptedAndZeroAndOneHundredAndOneAreNot() {
        given(archive.newestFirst(SATELLITE, 100)).willReturn(List.of());

        assertThat(mvc.get().uri("/satellites/99999/link-reports?limit=100")).hasStatusOk();
        assertThat(mvc.get().uri("/satellites/99999/link-reports?limit=0")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/satellites/99999/link-reports?limit=101")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Requirement("API-010")
    void invalidNoradIdIsABadRequest() {
        assertThat(mvc.get().uri("/satellites/0/link-reports")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(archive);
    }
}
