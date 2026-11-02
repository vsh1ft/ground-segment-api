package lt.vsh1ft.groundstation;

import static org.assertj.core.api.Assertions.assertThat;

import lt.vsh1ft.groundstation.pass.NoradId;
import lt.vsh1ft.groundstation.tle.CelestrakClient;
import lt.vsh1ft.groundstation.tle.OmmFixtures;
import lt.vsh1ft.groundstation.tle.StoredOmm;
import lt.vsh1ft.groundstation.tle.StoredOmmRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@SpringBootTest
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
    private StoredOmmRepository repository;

    @Test
    void startupForecastStoresTheDownloadedOmm() {
        final var stored = repository.findFirstByNoradIdOrderByDownloadedAtDesc(25544);

        assertThat(stored).hasValueSatisfying(omm -> {
            assertThat(omm.epoch()).isEqualTo(OmmFixtures.ISS.epoch());
            assertThat(omm.downloadedAt()).isNotNull();
        });
    }
}
