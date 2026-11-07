package lt.vsh1ft.groundstation.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import lt.vsh1ft.groundstation.elements.OmmFixtures;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;

class ElementSetTest {

    @Test
    @Requirement("IF-030")
    void storedOmmBecomesAnElementSetWithTheEpochInUtc() {
        final var downloadedAt = Instant.parse("2026-11-04T10:00:00Z");

        final var elements = ElementSet.from(OmmFixtures.ISS.withDownloadedAt(downloadedAt));

        assertThat(elements).isEqualTo(new ElementSet(Instant.parse("2026-11-01T18:56:25.712736Z"),
                15.49143202, 0.0007095, 51.6332, 339.1679, 336.8847, 23.1822, 0.00011503, 5.925e-5, 0, 999, 59308,
                downloadedAt));
    }
}
