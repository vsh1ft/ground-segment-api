package lt.vsh1ft.groundstation.tle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
@Import(PostgresTestcontainer.class)
@Sql(statements = "INSERT INTO satellite VALUES (25544, 'ISS (ZARYA)', '1998-067A', '2026-11-01T00:00:00Z')")
class StoredOmmRepositoryTest {

    private static final Instant MORNING = Instant.parse("2026-11-02T08:00:00Z");
    private static final Instant EVENING = Instant.parse("2026-11-02T20:00:00Z");

    @Autowired
    private StoredOmmRepository repository;

    @Test
    void savedOmmIsReadBackUnchanged() {
        final var saved = repository.save(OmmFixtures.ISS.withDownloadedAt(MORNING));

        assertThat(repository.findById(saved.id())).contains(saved);
    }

    @Test
    void latestDownloadComesFirst() {
        repository.save(OmmFixtures.ISS.withDownloadedAt(MORNING));
        final var newer = repository.save(withEpochOneDayLater(OmmFixtures.ISS).withDownloadedAt(EVENING));

        assertThat(repository.findFirstByNoradIdOrderByDownloadedAtDesc(25544)).contains(newer);
    }

    @Test
    void ommIsFoundBySatelliteAndEpoch() {
        final var saved = repository.save(OmmFixtures.ISS.withDownloadedAt(MORNING));

        assertThat(repository.findByNoradIdAndEpoch(25544, OmmFixtures.ISS.epoch())).contains(saved);
        assertThat(repository.findByNoradIdAndEpoch(99999, OmmFixtures.ISS.epoch())).isEmpty();
    }

    @Test
    void ommOfASatelliteOutsideTheCatalogIsRejected() {
        final var unknown = new StoredOmm(null, 99999, "UNKNOWN", "2026-001A", OmmFixtures.ISS.epoch(),
                15.0, 0.001, 51.6, 0, 0, 0, 0, "U", 1, 1, 0, 0, 0, MORNING);

        assertThatThrownBy(() -> repository.save(unknown)).hasStackTraceContaining("omm_satellite_fk");
    }

    @Test
    void sameSatelliteAndEpochCanBeStoredOnlyOnce() {
        repository.save(OmmFixtures.ISS.withDownloadedAt(MORNING));

        assertThatThrownBy(() -> repository.save(OmmFixtures.ISS.withDownloadedAt(EVENING)))
                .hasStackTraceContaining("duplicate key value violates unique constraint");
    }

    private static StoredOmm withEpochOneDayLater(StoredOmm omm) {
        return new StoredOmm(null, omm.noradId(), omm.objectName(), omm.objectId(), omm.epoch().plusDays(1),
                omm.meanMotion(), omm.eccentricity(), omm.inclination(), omm.raOfAscNode(),
                omm.argOfPericenter(), omm.meanAnomaly(), omm.ephemerisType(), omm.classificationType(),
                omm.elementSetNo(), omm.revAtEpoch(), omm.bstar(), omm.meanMotionDot(), omm.meanMotionDdot(),
                omm.downloadedAt());
    }
}
