package lt.vsh1ft.groundstation.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import javax.sql.DataSource;

import lt.vsh1ft.groundstation.PostgresTestcontainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJdbcTest(properties = "spring.flyway.target=3")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainer.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SatelliteMigrationTest {

    private static final Instant FIRST_DOWNLOAD = Instant.parse("2026-10-30T08:00:00Z");
    private static final Instant LATEST_DOWNLOAD = Instant.parse("2026-11-02T08:00:00Z");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void satellitesAreBackFilledFromStoredElements() {
        storeOmm("ISS", LocalDateTime.parse("2026-10-30T06:00:00"), FIRST_DOWNLOAD);
        storeOmm("ISS (ZARYA)", LocalDateTime.parse("2026-11-02T06:00:00"), LATEST_DOWNLOAD);

        Flyway.configure().dataSource(dataSource).load().migrate();

        final var satellite = jdbc.sql("SELECT * FROM satellite_summary").query(SatelliteSummary.class).single();
        assertThat(satellite).isEqualTo(new SatelliteSummary(25544, "ISS (ZARYA)", "1998-067A",
                FIRST_DOWNLOAD, Instant.parse("2026-11-02T06:00:00Z")));
    }

    private void storeOmm(String name, LocalDateTime epoch, Instant downloadedAt) {
        jdbc.sql("""
                        INSERT INTO omm (norad_id, object_name, object_id, epoch, mean_motion, eccentricity,
                            inclination, ra_of_asc_node, arg_of_pericenter, mean_anomaly, ephemeris_type,
                            classification_type, element_set_no, rev_at_epoch, bstar, mean_motion_dot,
                            mean_motion_ddot, downloaded_at)
                        VALUES (25544, ?, '1998-067A', ?, 15.49, 0.0007, 51.63, 339.17, 336.88, 23.18, 0, 'U',
                            999, 59308, 0.0001, 0.00006, 0, ?)
                        """)
                .params(name, epoch, Timestamp.from(downloadedAt))
                .update();
    }
}
