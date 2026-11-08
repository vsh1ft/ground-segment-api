package lt.vsh1ft.groundstation.satnogs;

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
@Import({PostgresTestcontainer.class, SatnogsImportState.class})
class SatnogsImportStateTest {

    private static final NoradId FRONTIERSAT = new NoradId(69015);

    @Autowired
    private SatnogsImportState state;

    @Test
    @Requirement("SAT-010")
    void importPositionIsRememberedPerSatelliteAndMovesForward() {
        assertThat(state.importedUntil(FRONTIERSAT)).isEmpty();

        state.save(FRONTIERSAT, Instant.parse("2026-11-08T18:00:00Z"));
        state.save(FRONTIERSAT, Instant.parse("2026-11-08T19:00:00.123Z"));
        state.save(new NoradId(25544), Instant.parse("2026-11-01T00:00:00Z"));

        assertThat(state.importedUntil(FRONTIERSAT)).hasValue(Instant.parse("2026-11-08T19:00:00.123Z"));
    }
}
