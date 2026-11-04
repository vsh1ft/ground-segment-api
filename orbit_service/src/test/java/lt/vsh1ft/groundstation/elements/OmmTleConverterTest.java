package lt.vsh1ft.groundstation.elements;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import lt.vsh1ft.groundstation.shared.OrekitDataLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OmmTleConverterTest {

    private static final String TLE_LINE_1 = "1 25544U 98067A   26305.78918649  .00005925  00000-0  11503-3 0  9992";
    private static final String TLE_LINE_2 = "2 25544  51.6332 339.1679 0007095 336.8847  23.1822 15.49143202593084";

    @BeforeAll
    static void loadOrekitData() {
        new OrekitDataLoader(new File("orekit-data"));
    }

    @Test
    void tleMatchesTheTlePublishedForTheSameElementSet() {
        final var tle = OmmTleConverter.toTle(OmmFixtures.ISS);

        assertThat(tle.getLine1()).isEqualTo(TLE_LINE_1);
        assertThat(tle.getLine2()).isEqualTo(TLE_LINE_2);
    }
}
