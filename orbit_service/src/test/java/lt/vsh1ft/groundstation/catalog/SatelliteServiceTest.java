package lt.vsh1ft.groundstation.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.time.ZoneOffset;

import lt.vsh1ft.groundstation.PostgresTestcontainer;
import lt.vsh1ft.groundstation.elements.CelestrakException;
import lt.vsh1ft.groundstation.elements.InvalidTleException;
import lt.vsh1ft.groundstation.elements.OmmArchive;
import lt.vsh1ft.groundstation.elements.OmmDownloader;
import lt.vsh1ft.groundstation.elements.OmmFixtures;
import lt.vsh1ft.groundstation.elements.OrbitDataUnavailableException;
import lt.vsh1ft.groundstation.elements.SatelliteNotFoundException;
import lt.vsh1ft.groundstation.elements.StoredOmm;
import lt.vsh1ft.groundstation.elements.StoredOmmRepository;
import lt.vsh1ft.groundstation.elements.TleReader;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
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

    @MockitoBean
    private TleReader tles;

    @Test
    @Requirement({"CAT-010", "CAT-100"})
    void addedSatelliteTakesNameAndDesignatorFromCelestrak() {
        celestrakKnows(OmmFixtures.ISS);

        service.add(ISS);

        assertThat(service.get(ISS)).isEqualTo(new SatelliteSummary(25544, "ISS (ZARYA)", "1998-067A",
                DOWNLOADED_AT, OmmFixtures.ISS.epoch().toInstant(ZoneOffset.UTC)));
    }

    @Test
    @Requirement("CAT-010")
    void addedSatelliteHasItsFirstElementsStored() {
        celestrakKnows(OmmFixtures.ISS);

        service.add(ISS);

        assertThat(elements.findFirstByNoradIdOrderByEpochDesc(25544))
                .hasValueSatisfying(omm -> assertThat(omm.epoch()).isEqualTo(OmmFixtures.ISS.epoch()));
    }

    @Test
    @Requirement("CAT-020")
    void satelliteAlreadyInTheCatalogIsAConflict() {
        celestrakKnows(OmmFixtures.ISS);
        service.add(ISS);

        assertThatThrownBy(() -> service.add(ISS)).isInstanceOf(SatelliteAlreadyInCatalogException.class)
                .hasMessage("NORAD ID 25544 is already in the catalog");
    }

    @Test
    @Requirement("CAT-020")
    void satelliteCelestrakDoesNotKnowIsNotFoundAndNotAdded() {
        given(downloader.download(UNKNOWN)).willThrow(new SatelliteNotFoundException(UNKNOWN));

        assertThatThrownBy(() -> service.add(UNKNOWN)).isInstanceOf(SatelliteNotFoundException.class);
        assertThatThrownBy(() -> service.get(UNKNOWN)).isInstanceOf(SatelliteNotInCatalogException.class);
    }

    @Test
    @Requirement("CAT-020")
    void satelliteCannotBeAddedWhileCelestrakIsDown() {
        given(downloader.download(ISS)).willThrow(new CelestrakException("CelesTrak request for 25544 failed"));

        assertThatThrownBy(() -> service.add(ISS)).isInstanceOf(OrbitDataUnavailableException.class)
                .hasMessage("Can't add NORAD ID 25544 to the catalog: CelesTrak is unavailable");
    }

    @Test
    @Requirement("CAT-090")
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
    @Requirement("CAT-040")
    void deletedSatelliteIsRemovedWithItsElements() {
        celestrakKnows(OmmFixtures.ISS);
        service.add(ISS);

        service.delete(ISS);

        assertThatThrownBy(() -> service.get(ISS)).isInstanceOf(SatelliteNotInCatalogException.class);
        assertThat(elements.findFirstByNoradIdOrderByEpochDesc(25544)).isEmpty();
    }

    @Test
    @Requirement("CAT-090")
    void deletingASatelliteOutsideTheCatalogIsNotFound() {
        assertThatThrownBy(() -> service.delete(UNKNOWN)).isInstanceOf(SatelliteNotInCatalogException.class);
    }

    @Test
    @Requirement("CAT-050")
    void storedSatelliteOutsideTheCatalogIsAdded() {
        service.store(OmmFixtures.ISS.withDownloadedAt(DOWNLOADED_AT));

        assertThat(service.get(ISS).name()).isEqualTo("ISS (ZARYA)");
        assertThat(service.all()).containsExactly(ISS);
    }

    @Test
    @Requirement("CAT-080")
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
    @Requirement("ELM-020")
    void elementHistoryIsNewestFirstAndOnlyForThatSatellite() {
        addIssNoaaAndMeteor();
        service.store(OmmFixtures.withEpochDaysLater(OmmFixtures.ISS, 1).withDownloadedAt(DOWNLOADED_AT));

        final var history = service.elementHistory(ISS, FIRST_PAGE);

        final var epoch = OmmFixtures.ISS.epoch();
        assertThat(history.getContent()).extracting(ElementSet::epoch).containsExactly(
                epoch.plusDays(1).toInstant(ZoneOffset.UTC), epoch.toInstant(ZoneOffset.UTC));
    }

    @Test
    @Requirement("CAT-090")
    void elementHistoryOfASatelliteOutsideTheCatalogIsNotFound() {
        assertThatThrownBy(() -> service.elementHistory(UNKNOWN, FIRST_PAGE))
                .isInstanceOf(SatelliteNotInCatalogException.class);
    }

    @Test
    @Requirement("CAT-030")
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
    @Requirement("CAT-030")
    void searchMatchesPartOfTheNameIgnoringCase() {
        addIssNoaaAndMeteor();

        assertThat(service.search("noaa", FIRST_PAGE).getContent()).extracting(SatelliteSummary::noradId)
                .containsExactly(33591);
    }

    @Test
    @Requirement("CAT-030")
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

    @Test
    @Requirement("CAT-110")
    void uploadedTleAddsASatelliteOutsideTheCatalogUnderTheTlesName() {
        given(tles.read(UNKNOWN, "tle")).willReturn(uploaded(OmmFixtures.ISS, 99999, "GSAPI-SIM"));

        final var uploaded = service.upload(UNKNOWN, "tle");

        assertThat(uploaded.addedToCatalog()).isTrue();
        assertThat(uploaded.elements().epoch()).isEqualTo(OmmFixtures.ISS.epoch().toInstant(ZoneOffset.UTC));
        assertThat(service.get(UNKNOWN)).isEqualTo(new SatelliteSummary(99999, "GSAPI-SIM", "1998-067A",
                DOWNLOADED_AT, OmmFixtures.ISS.epoch().toInstant(ZoneOffset.UTC)));
    }

    @Test
    @Requirement({"CAT-110", "CAT-100"})
    void uploadedTleOfACatalogSatelliteAddsAnElementSetAndKeepsItsName() {
        celestrakKnows(OmmFixtures.ISS);
        service.add(ISS);
        final var newer = OmmFixtures.withEpochDaysLater(OmmFixtures.ISS, 2);
        given(tles.read(ISS, "tle")).willReturn(uploaded(newer, 25544, "MY ISS"));

        final var uploaded = service.upload(ISS, "tle");

        assertThat(uploaded.addedToCatalog()).isFalse();
        assertThat(service.get(ISS).name()).isEqualTo("ISS (ZARYA)");
        assertThat(service.get(ISS).latestEpoch()).isEqualTo(newer.epoch().toInstant(ZoneOffset.UTC));
        assertThat(service.elementHistory(ISS, FIRST_PAGE).getTotalElements()).isEqualTo(2);
    }

    @Test
    @Requirement("CAT-110")
    void uploadingTheSameEpochTwiceStoresOneElementSet() {
        given(tles.read(UNKNOWN, "tle")).willReturn(uploaded(OmmFixtures.ISS, 99999, "GSAPI-SIM"));
        service.upload(UNKNOWN, "tle");

        assertThat(service.upload(UNKNOWN, "tle").addedToCatalog()).isFalse();
        assertThat(service.elementHistory(UNKNOWN, FIRST_PAGE).getTotalElements()).isEqualTo(1);
    }

    @Test
    @Requirement("CAT-110")
    void invalidTleAddsNothing() {
        given(tles.read(UNKNOWN, "tle")).willThrow(new InvalidTleException("The name line is longer than 100 "
                + "characters"));

        assertThatThrownBy(() -> service.upload(UNKNOWN, "tle")).isInstanceOf(InvalidTleException.class);
        assertThatThrownBy(() -> service.get(UNKNOWN)).isInstanceOf(SatelliteNotInCatalogException.class);
    }

    private static StoredOmm uploaded(StoredOmm omm, int noradId, String name) {
        return new StoredOmm(null, noradId, name, omm.objectId(), omm.epoch(), omm.meanMotion(),
                omm.eccentricity(), omm.inclination(), omm.raOfAscNode(), omm.argOfPericenter(), omm.meanAnomaly(),
                omm.ephemerisType(), omm.classificationType(), omm.elementSetNo(), omm.revAtEpoch(), omm.bstar(),
                omm.meanMotionDot(), omm.meanMotionDdot(), DOWNLOADED_AT);
    }
}
