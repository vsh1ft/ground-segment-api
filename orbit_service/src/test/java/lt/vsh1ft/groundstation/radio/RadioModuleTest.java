package lt.vsh1ft.groundstation.radio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.List;

import lt.vsh1ft.groundstation.PostgresTestcontainer;
import lt.vsh1ft.groundstation.catalog.CatalogRefreshed;
import lt.vsh1ft.groundstation.catalog.SatelliteService;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.modulith.test.Scenario;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

@ApplicationModuleTest(BootstrapMode.ALL_DEPENDENCIES)
@Import(PostgresTestcontainer.class)
@Sql(statements = "INSERT INTO satellite VALUES (25544, 'ISS (ZARYA)', '1998-067A', '2026-11-01T00:00:00Z') "
        + "ON CONFLICT DO NOTHING")
class RadioModuleTest {

    private static final NoradId ISS = new NoradId(25544);
    private static final SatnogsTransmitter ISS_APRS = new SatnogsTransmitter("ZJxCeQmih9zDfYNVrB4wRN", 25544,
            "Mode V APRS", "Transceiver", "active", 145825000L, null, 145825000L, null, "AFSK", "AFSK", 1200.0,
            false, Instant.parse("2019-06-16T08:49:33.586159Z"));

    @Autowired
    private TransmitterService transmitters;

    @Autowired
    private TransmitterRepository repository;

    @Autowired
    private SatelliteService catalog;

    @MockitoBean
    private SatnogsClient satnogs;

    @MockitoBean
    private TaskScheduler scheduler;

    @Test
    @Requirement("RAD-010")
    void finishedCatalogRefreshSyncsTheTransmitters(Scenario scenario) {
        given(satnogs.transmitters()).willReturn(List.of(ISS_APRS));

        scenario.publish(new CatalogRefreshed())
                .andWaitForStateChange(() -> transmitters.transmitters(ISS, null), found -> !found.isEmpty())
                .andVerify(found -> assertThat(found).extracting(Transmitter::description)
                        .containsExactly("Mode V APRS"));
    }

    @Test
    @Requirement("CAT-040")
    void removedSatelliteTakesItsTransmittersWithIt() {
        transmitters.replaceAll(List.of(ISS_APRS.toTransmitter(Instant.parse("2026-11-07T12:00:00Z"))));

        catalog.delete(ISS);

        assertThat(repository.findAll()).isEmpty();
    }
}
