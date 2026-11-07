package lt.vsh1ft.groundstation.radio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;

import java.time.Instant;
import java.util.List;

import lt.vsh1ft.groundstation.PostgresTestcontainer;
import lt.vsh1ft.groundstation.catalog.SatelliteNotInCatalogException;
import lt.vsh1ft.groundstation.catalog.SatelliteService;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainer.class, TransmitterService.class})
@Sql(statements = {
        "INSERT INTO satellite VALUES (25544, 'ISS (ZARYA)', '1998-067A', '2026-11-01T00:00:00Z')",
        "INSERT INTO satellite VALUES (33591, 'NOAA 19', '2009-005A', '2026-11-01T00:00:00Z')"})
class TransmitterServiceTest {

    private static final NoradId ISS = new NoradId(25544);
    private static final Instant MORNING = Instant.parse("2026-11-07T08:00:00Z");
    private static final Instant EVENING = Instant.parse("2026-11-07T20:00:00Z");

    @Autowired
    private TransmitterService service;

    @MockitoBean
    private SatelliteService catalog;

    @Test
    void transmittersAreSortedByDownlinkFrequencyWithoutDownlinkLast() {
        service.replaceAll(List.of(
                transmitter("uhf", 25544, 437800000L, TransmitterStatus.ACTIVE, MORNING),
                transmitter("no-downlink", 25544, null, TransmitterStatus.ACTIVE, MORNING),
                transmitter("vhf", 25544, 145800000L, TransmitterStatus.INACTIVE, MORNING),
                transmitter("noaa", 33591, 137100000L, TransmitterStatus.ACTIVE, MORNING)));

        assertThat(service.transmitters(ISS, null)).extracting(TransmitterResponse::uuid)
                .containsExactly("vhf", "uhf", "no-downlink");
    }

    @Test
    void statusFilterKeepsOnlyMatchingTransmitters() {
        service.replaceAll(List.of(
                transmitter("uhf", 25544, 437800000L, TransmitterStatus.ACTIVE, MORNING),
                transmitter("vhf", 25544, 145800000L, TransmitterStatus.INACTIVE, MORNING)));

        assertThat(service.transmitters(ISS, TransmitterStatus.ACTIVE)).extracting(TransmitterResponse::uuid)
                .containsExactly("uhf");
    }

    @Test
    void replacingRemovesTransmittersSatnogsNoLongerLists() {
        service.replaceAll(List.of(
                transmitter("uhf", 25544, 437800000L, TransmitterStatus.ACTIVE, MORNING),
                transmitter("vhf", 25544, 145800000L, TransmitterStatus.ACTIVE, MORNING)));

        service.replaceAll(List.of(transmitter("uhf", 25544, 437800000L, TransmitterStatus.INACTIVE, EVENING)));

        assertThat(service.transmitters(ISS, null)).singleElement()
                .satisfies(transmitter -> assertThat(transmitter.status()).isEqualTo(TransmitterStatus.INACTIVE));
    }

    @Test
    void lastSyncIsTheNewestSyncTime() {
        service.replaceAll(List.of(transmitter("uhf", 25544, 437800000L, TransmitterStatus.ACTIVE, EVENING)));

        assertThat(service.lastSync()).contains(EVENING);
    }

    @Test
    void nothingSyncedMeansNoLastSync() {
        assertThat(service.lastSync()).isEmpty();
    }

    @Test
    void satelliteOutsideTheCatalogIsNotFound() {
        willThrow(new SatelliteNotInCatalogException(ISS)).given(catalog).ensureInCatalog(ISS);

        assertThatThrownBy(() -> service.transmitters(ISS, null)).isInstanceOf(SatelliteNotInCatalogException.class);
    }

    private static Transmitter transmitter(String uuid, int noradId, Long downlinkHz, TransmitterStatus status,
                                           Instant syncedAt) {
        return new Transmitter(uuid, noradId, "Beacon " + uuid, TransmitterType.TRANSMITTER, status, null, null,
                downlinkHz, null, "FM", null, null, false, Instant.parse("2019-06-16T08:49:33Z"), syncedAt);
    }
}
