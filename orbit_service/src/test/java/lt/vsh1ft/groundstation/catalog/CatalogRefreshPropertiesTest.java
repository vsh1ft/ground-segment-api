package lt.vsh1ft.groundstation.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.List;

import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;

class CatalogRefreshPropertiesTest {

    @Test
    @Requirement("OPS-040")
    void intervalShorterThanTwoHoursIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CatalogRefreshProperties(Duration.ofMinutes(119), List.of("stations")))
                .withMessage("groundstation.catalog.refresh.interval must be at least PT2H because CelesTrak asks "
                        + "not to download the same data more often, was PT1H59M");
    }

    @Test
    @Requirement("OPS-040")
    void intervalOfTwoHoursIsAccepted() {
        assertThat(new CatalogRefreshProperties(Duration.ofHours(2), List.of("stations")).interval())
                .isEqualTo(Duration.ofHours(2));
    }
}
