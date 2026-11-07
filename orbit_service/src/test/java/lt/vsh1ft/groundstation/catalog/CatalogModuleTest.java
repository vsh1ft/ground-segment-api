package lt.vsh1ft.groundstation.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Instant;

import lt.vsh1ft.groundstation.PostgresTestcontainer;
import lt.vsh1ft.groundstation.elements.OmmDownloader;
import lt.vsh1ft.groundstation.elements.OmmFixtures;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@ApplicationModuleTest(BootstrapMode.DIRECT_DEPENDENCIES)
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class CatalogModuleTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private OmmDownloader downloader;

    @MockitoBean
    private TaskScheduler scheduler;

    @Test
    void satelliteIsAddedFoundAndRemovedOverHttp() {
        given(downloader.download(new NoradId(25544)))
                .willReturn(OmmFixtures.ISS.withDownloadedAt(Instant.parse("2026-11-04T10:00:00Z")));

        assertThat(mvc.post().uri("/satellites/25544")).hasStatus(HttpStatus.CREATED);
        assertThat(mvc.get().uri("/satellites/25544/elements")).hasStatusOk()
                .bodyJson().extractingPath("$.content[0].epoch").isEqualTo("2026-11-01T18:56:25.712736Z");
        assertThat(mvc.get().uri("/satellites?search=zarya")).hasStatusOk()
                .bodyJson().extractingPath("$.content[0].noradId").isEqualTo(25544);
        assertThat(mvc.post().uri("/satellites/25544")).hasStatus(HttpStatus.CONFLICT);

        assertThat(mvc.delete().uri("/satellites/25544")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/satellites/25544")).hasStatus(HttpStatus.NOT_FOUND);
    }
}
