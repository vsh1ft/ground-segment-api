package lt.vsh1ft.groundstation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;

import lt.vsh1ft.groundstation.elements.CelestrakClient;
import lt.vsh1ft.groundstation.elements.OmmFixtures;
import lt.vsh1ft.groundstation.elements.StoredOmm;
import lt.vsh1ft.groundstation.elements.StoredOmmRepository;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainer.class, GroundStationApplicationTest.OfflineCelestrak.class})
class GroundStationApplicationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class OfflineCelestrak {

        @Bean
        @Primary
        CelestrakClient offlineCelestrakClient() {
            return new CelestrakClient(null) {
                @Override
                public StoredOmm latest(NoradId satellite) {
                    return OmmFixtures.ISS;
                }
            };
        }
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private StoredOmmRepository repository;

    @MockitoBean(reset = MockReset.NONE)
    private TaskScheduler scheduler;

    @Test
    void catalogRefreshIsScheduledAtStartup() {
        verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), eq(Duration.ofHours(4)));
    }

    @Test
    void passesAreServedForASatelliteAndStationAddedOverTheApi() {
        assertThat(mvc.post().uri("/satellites/25544")).hasStatus(HttpStatus.CREATED);
        assertThat(mvc.get().uri("/satellites?search=iss")).hasStatusOk()
                .bodyJson().extractingPath("$.content[0].noradId").isEqualTo(25544);

        final var created = mvc.post().uri("/stations").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Vilnius", "latitudeDeg": 54.67519, "longitudeDeg": 25.23422, "altitudeM": 114,
                         "minElevationDeg": 5}
                        """)
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        final var stationUri = created.getResponse().getHeader("Location");
        final var stationId = stationUri.substring(stationUri.lastIndexOf('/') + 1);

        assertThat(mvc.get().uri("/passes?satellite=25544&station=" + stationId)).hasStatusOk()
                .bodyJson().extractingPath("$.station").isEqualTo(stationId);

        final var stored = repository.findFirstByNoradIdOrderByEpochDesc(25544);
        assertThat(stored).hasValueSatisfying(omm -> {
            assertThat(omm.epoch()).isEqualTo(OmmFixtures.ISS.epoch());
            assertThat(omm.downloadedAt()).isNotNull();
        });
    }
}
