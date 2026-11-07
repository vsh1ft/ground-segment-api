package lt.vsh1ft.groundstation.elements;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import lt.vsh1ft.groundstation.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainer.class, OmmArchive.class})
@Sql(statements = "INSERT INTO satellite VALUES (25544, 'ISS (ZARYA)', '1998-067A', '2026-11-01T00:00:00Z')")
class OmmArchiveTest {

    private static final Instant MORNING = Instant.parse("2026-11-02T08:00:00Z");
    private static final Instant EVENING = Instant.parse("2026-11-02T20:00:00Z");

    @Autowired
    private OmmArchive archive;

    @Autowired
    private StoredOmmRepository repository;

    @Test
    void newEpochIsKeptAlongsideTheOlderOnes() {
        archive.save(OmmFixtures.ISS.withDownloadedAt(MORNING));
        archive.save(withEpochOneDayLater(OmmFixtures.ISS).withDownloadedAt(EVENING));

        assertThat(repository.findAll()).extracting(StoredOmm::epoch)
                .containsExactlyInAnyOrder(OmmFixtures.ISS.epoch(), OmmFixtures.ISS.epoch().plusDays(1));
    }

    @Test
    void sameEpochDownloadedAgainOnlyUpdatesTheDownloadTime() {
        archive.save(OmmFixtures.ISS.withDownloadedAt(MORNING));
        archive.save(OmmFixtures.ISS.withDownloadedAt(EVENING));

        assertThat(repository.findAll()).singleElement()
                .satisfies(omm -> assertThat(omm.downloadedAt()).isEqualTo(EVENING));
    }

    @Test
    void lastDownloadIsTheNewestDownloadTime() {
        archive.save(withEpochOneDayLater(OmmFixtures.ISS).withDownloadedAt(EVENING));
        archive.save(OmmFixtures.ISS.withDownloadedAt(MORNING));

        assertThat(archive.lastDownload()).contains(EVENING);
    }

    @Test
    void nothingDownloadedMeansNoLastDownload() {
        assertThat(archive.lastDownload()).isEmpty();
    }

    private static StoredOmm withEpochOneDayLater(StoredOmm omm) {
        return new StoredOmm(null, omm.noradId(), omm.objectName(), omm.objectId(), omm.epoch().plusDays(1),
                omm.meanMotion(), omm.eccentricity(), omm.inclination(), omm.raOfAscNode(),
                omm.argOfPericenter(), omm.meanAnomaly(), omm.ephemerisType(), omm.classificationType(),
                omm.elementSetNo(), omm.revAtEpoch(), omm.bstar(), omm.meanMotionDot(), omm.meanMotionDdot(),
                omm.downloadedAt());
    }
}
