package lt.vsh1ft.groundstation.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.time.ZoneOffset;

import lt.vsh1ft.groundstation.PostgresTestcontainer;
import lt.vsh1ft.groundstation.elements.CelestrakException;
import lt.vsh1ft.groundstation.elements.OmmArchive;
import lt.vsh1ft.groundstation.elements.OmmDownloader;
import lt.vsh1ft.groundstation.elements.OmmFixtures;
import lt.vsh1ft.groundstation.elements.OrbitDataUnavailableException;
import lt.vsh1ft.groundstation.elements.SatelliteNotFoundException;
import lt.vsh1ft.groundstation.elements.StoredOmm;
import lt.vsh1ft.groundstation.elements.StoredOmmRepository;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainer.class, SatelliteService.class, OmmArchive.class})
class SatelliteServiceTest {

    private static final Instant DOWNLOADED_AT = Instant.parse("2026-11-04T10:00:00Z");
    private static final NoradId ISS = new NoradId(25544);
    private static final NoradId NOAA_19 = new NoradId(33591);
    private static final NoradId METEOR_M2_3 = new NoradId(57166);
    private static final NoradId UNKNOWN = new NoradId(99999);
    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20);

    @Autowired
    private SatelliteService service;

    @Autowired
    private StoredOmmRepository elements;

    @MockitoBean
    private OmmDownloader downloader;

    @Test
    void addedSatelliteTakesNameAndDesignatorFromCelestrak() {
        celestrakKnows(OmmFixtures.ISS);

        service.add(ISS);

        assertThat(service.get(ISS)).isEqualTo(new SatelliteSummary(25544, "ISS (ZARYA)", "1998-067A",
                DOWNLOADED_AT, OmmFixtures.ISS.epoch().toInstant(ZoneOffset.UTC)));
    }

    @Test
    void addedSatelliteHasItsFirstElementsStored() {
        celestrakKnows(OmmFixtures.ISS);

        service.add(ISS);

        assertThat(elements.findFirstByNoradIdOrderByEpochDesc(25544))
                .hasValueSatisfying(omm -> assertThat(omm.epoch()).isEqualTo(OmmFixtures.ISS.epoch()));
    }

    @Test
    void satelliteAlreadyInTheCatalogIsAConflict() {
        celestrakKnows(OmmFixtures.ISS);
        service.add(ISS);

        assertThatThrownBy(() -> service.add(ISS)).isInstanceOf(SatelliteAlreadyInCatalogException.class)
                .hasMessage("NORAD ID 25544 is already in the catalog");
    }

    @Test
    void satelliteCelestrakDoesNotKnowIsNotFoundAndNotAdded() {
        given(downloader.download(UNKNOWN)).willThrow(new SatelliteNotFoundException(UNKNOWN));

        assertThatThrownBy(() -> service.add(UNKNOWN)).isInstanceOf(SatelliteNotFoundException.class);
        assertThatThrownBy(() -> service.get(UNKNOWN)).isInstanceOf(SatelliteNotInCatalogException.class);
    }

    @Test
    void satelliteCannotBeAddedWhileCelestrakIsDown() {
        given(downloader.download(ISS)).willThrow(new CelestrakException("CelesTrak request for 25544 failed"));

        assertThatThrownBy(() -> service.add(ISS)).isInstanceOf(OrbitDataUnavailableException.class)
                .hasMessage("Can't add NORAD ID 25544 to the catalog: CelesTrak is unavailable");
    }

    @Test
    void satelliteOutsideTheCatalogIsNotFound() {
        assertThatThrownBy(() -> service.get(UNKNOWN)).isInstanceOf(SatelliteNotInCatalogException.class)
                .hasMessage("No satellite with NORAD ID 99999 in the catalog");
        assertThatThrownBy(() -> service.ensureInCatalog(UNKNOWN)).isInstanceOf(SatelliteNotInCatalogException.class);
    }

    @Test
    void catalogSatelliteIsAccepted() {
        celestrakKnows(OmmFixtures.ISS);
        service.add(ISS);

        assertThatNoException().isThrownBy(() -> service.ensureInCatalog(ISS));
    }

    @Test
    void deletedSatelliteIsRemovedWithItsElements() {
        celestrakKnows(OmmFixtures.ISS);
        service.add(ISS);

        service.delete(ISS);

        assertThatThrownBy(() -> service.get(ISS)).isInstanceOf(SatelliteNotInCatalogException.class);
        assertThat(elements.findFirstByNoradIdOrderByEpochDesc(25544)).isEmpty();
    }

    @Test
    void deletingASatelliteOutsideTheCatalogIsNotFound() {
        assertThatThrownBy(() -> service.delete(UNKNOWN)).isInstanceOf(SatelliteNotInCatalogException.class);
    }

    @Test
    void storedSatelliteOutsideTheCatalogIsAdded() {
        service.store(OmmFixtures.ISS.withDownloadedAt(DOWNLOADED_AT));

        assertThat(service.get(ISS).name()).isEqualTo("ISS (ZARYA)");
        assertThat(service.all()).containsExactly(ISS);
    }

    @Test
    void storedSatelliteAlreadyInTheCatalogTakesTheNewNameButKeepsWhenItWasAdded() {
        celestrakKnows(OmmFixtures.ISS);
        service.add(ISS);
        final var later = DOWNLOADED_AT.plusSeconds(4 * 3600);

        service.store(renamed(OmmFixtures.ISS, ISS, "ISS", "1998-067A").withDownloadedAt(later));

        assertThat(service.get(ISS)).satisfies(summary -> {
            assertThat(summary.name()).isEqualTo("ISS");
            assertThat(summary.addedAt()).isEqualTo(DOWNLOADED_AT);
        });
        assertThat(elements.findFirstByNoradIdOrderByEpochDesc(25544))
                .hasValueSatisfying(omm -> assertThat(omm.downloadedAt()).isEqualTo(later));
    }

    @Test
    void searchWithoutATermPagesThroughEverySatelliteByName() {
        addIssNoaaAndMeteor();

        final var first = service.search(null, PageRequest.of(0, 2));
        final var second = service.search(" ", PageRequest.of(1, 2));

        assertThat(first.getContent()).extracting(SatelliteSummary::name).containsExactly("ISS (ZARYA)", "METEOR-M2 3");
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(second.getContent()).extracting(SatelliteSummary::name).containsExactly("NOAA 19");
    }

    @Test
    void searchMatchesPartOfTheNameIgnoringCase() {
        addIssNoaaAndMeteor();

        assertThat(service.search("noaa", FIRST_PAGE).getContent()).extracting(SatelliteSummary::noradId)
                .containsExactly(33591);
    }

    @Test
    void numericSearchMatchesTheNoradIdExactly() {
        addIssNoaaAndMeteor();

        assertThat(service.search("33591", FIRST_PAGE).getContent()).extracting(SatelliteSummary::name)
                .containsExactly("NOAA 19");
        assertThat(service.search("3359", FIRST_PAGE).getContent()).isEmpty();
    }

    private void addIssNoaaAndMeteor() {
        celestrakKnows(OmmFixtures.ISS);
        celestrakKnows(renamed(OmmFixtures.ISS, NOAA_19, "NOAA 19", "2009-005A"));
        celestrakKnows(renamed(OmmFixtures.ISS, METEOR_M2_3, "METEOR-M2 3", "2023-091A"));
        service.add(ISS);
        service.add(NOAA_19);
        service.add(METEOR_M2_3);
    }

    private void celestrakKnows(StoredOmm omm) {
        given(downloader.download(new NoradId(omm.noradId()))).willReturn(omm.withDownloadedAt(DOWNLOADED_AT));
    }

    private static StoredOmm renamed(StoredOmm omm, NoradId noradId, String name, String designator) {
        return new StoredOmm(null, noradId.value(), name, designator, omm.epoch(),
                omm.meanMotion(), omm.eccentricity(), omm.inclination(), omm.raOfAscNode(),
                omm.argOfPericenter(), omm.meanAnomaly(), omm.ephemerisType(), omm.classificationType(),
                omm.elementSetNo(), omm.revAtEpoch(), omm.bstar(), omm.meanMotionDot(), omm.meanMotionDdot(),
                omm.downloadedAt());
    }
}
