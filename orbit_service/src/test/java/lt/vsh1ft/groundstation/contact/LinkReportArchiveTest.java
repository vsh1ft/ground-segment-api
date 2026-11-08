package lt.vsh1ft.groundstation.contact;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import lt.vsh1ft.groundstation.PostgresTestcontainer;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainer.class, LinkReportArchive.class})
class LinkReportArchiveTest {

    private static final NoradId SATELLITE = new NoradId(99999);
    private static final Instant FIRST_START = Instant.parse("2026-11-08T08:21:40.123456Z");
    private static final Instant SECOND_START = Instant.parse("2026-11-08T09:55:12Z");

    @Autowired
    private LinkReportArchive archive;

    @Test
    @Requirement("CON-050")
    void reportOfAPassIsUpdatedInPlaceAndListedNewestPassFirst() {
        archive.save(report(FIRST_START, 10, 1, FIRST_START.plusSeconds(10)));
        archive.save(report(SECOND_START, 40, 0, SECOND_START.plusSeconds(10)));
        archive.save(report(FIRST_START, 1700, 120, FIRST_START.plusSeconds(560)));
        archive.save(new LinkReport(25544, FIRST_START, FIRST_START.plusSeconds(560), 5, 0, 0, 0, FIRST_START));

        assertThat(archive.newestFirst(SATELLITE, 20)).containsExactly(
                report(SECOND_START, 40, 0, SECOND_START.plusSeconds(10)),
                report(FIRST_START, 1700, 120, FIRST_START.plusSeconds(560)));
        assertThat(archive.newestFirst(SATELLITE, 1)).hasSize(1);
    }

    private static LinkReport report(Instant passStart, long received, long lost, Instant updatedAt) {
        return new LinkReport(99999, passStart, passStart.plusSeconds(560), received, lost, lost / 3, received / 2,
                updatedAt);
    }
}
