package lt.vsh1ft.groundstation.elements;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import lt.vsh1ft.groundstation.PostgresTestcontainer;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
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
    @Requirement("ELM-010")
    void newEpochIsKeptAlongsideTheOlderOnes() {
        archive.save(OmmFixtures.ISS.withDownloadedAt(MORNING));
        archive.save(OmmFixtures.withEpochDaysLater(OmmFixtures.ISS, 1).withDownloadedAt(EVENING));

        assertThat(repository.findAll()).extracting(StoredOmm::epoch)
                .containsExactlyInAnyOrder(OmmFixtures.ISS.epoch(), OmmFixtures.ISS.epoch().plusDays(1));
    }

    @Test
    @Requirement("ELM-010")
    void sameEpochDownloadedAgainOnlyUpdatesTheDownloadTime() {
        archive.save(OmmFixtures.ISS.withDownloadedAt(MORNING));
        archive.save(OmmFixtures.ISS.withDownloadedAt(EVENING));

        assertThat(repository.findAll()).singleElement()
                .satisfies(omm -> assertThat(omm.downloadedAt()).isEqualTo(EVENING));
    }

    @Test
    void lastDownloadIsTheNewestDownloadTime() {
        archive.save(OmmFixtures.withEpochDaysLater(OmmFixtures.ISS, 1).withDownloadedAt(EVENING));
        archive.save(OmmFixtures.ISS.withDownloadedAt(MORNING));

        assertThat(archive.lastDownload()).contains(EVENING);
    }

    @Test
    @Requirement("ELM-020")
    void historyIsNewestEpochFirstOnePageAtATime() {
        archive.save(OmmFixtures.ISS.withDownloadedAt(MORNING));
        archive.save(OmmFixtures.withEpochDaysLater(OmmFixtures.ISS, 2).withDownloadedAt(MORNING));
        archive.save(OmmFixtures.withEpochDaysLater(OmmFixtures.ISS, 1).withDownloadedAt(MORNING));

        final var first = archive.history(new NoradId(25544), PageRequest.of(0, 2));
        final var second = archive.history(new NoradId(25544), PageRequest.of(1, 2));

        final var epoch = OmmFixtures.ISS.epoch();
        assertThat(first.getContent()).extracting(StoredOmm::epoch).containsExactly(epoch.plusDays(2), epoch.plusDays(1));
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(second.getContent()).extracting(StoredOmm::epoch).containsExactly(epoch);
    }

    @Test
    void nothingDownloadedMeansNoLastDownload() {
        assertThat(archive.lastDownload()).isEmpty();
    }
}
