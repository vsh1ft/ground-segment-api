package lt.vsh1ft.groundstation.elements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.shared.OrekitDataLoader;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TleReaderTest {

    private static final NoradId ISS = new NoradId(25544);
    private static final Instant NOW = Instant.parse("2026-11-08T09:00:00Z");
    private static final String LINE_1 = "1 25544U 98067A   26305.78918649  .00005925  00000-0  11503-3 0  9992";
    private static final String LINE_2 = "2 25544  51.6332 339.1679 0007095 336.8847  23.1822 15.49143202593084";

    private final TleReader reader = new TleReader(Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeAll
    static void loadOrekitData() {
        new OrekitDataLoader(new File("orekit-data"));
    }

    @Test
    @Requirement("CAT-110")
    void tleIsStoredAsTheElementSetItDescribes() {
        final var omm = reader.read(ISS, "ISS (ZARYA)\n" + LINE_1 + "\n" + LINE_2 + "\n");

        assertThat(omm.noradId()).isEqualTo(25544);
        assertThat(omm.objectName()).isEqualTo("ISS (ZARYA)");
        assertThat(omm.objectId()).isEqualTo("1998-067A");
        assertThat(omm.downloadedAt()).isEqualTo(NOW);
        assertThat(omm.meanMotion()).isEqualTo(15.49143202, offset(1e-12));
        final var tle = OmmTleConverter.toTle(omm);
        assertThat(tle.getLine1()).isEqualTo(LINE_1);
        assertThat(tle.getLine2()).isEqualTo(LINE_2);
    }

    @Test
    @Requirement("CAT-110")
    void windowsLineEndingsBlankLinesAndPaddingAreAccepted() {
        final var omm = reader.read(ISS, "\r\n  ISS (ZARYA)  \r\n\r\n" + LINE_1 + "  \r\n" + LINE_2 + "\r\n\r\n");

        assertThat(omm.objectName()).isEqualTo("ISS (ZARYA)");
        assertThat(OmmTleConverter.toTle(omm).getLine2()).isEqualTo(LINE_2);
    }

    @Test
    @Requirement("CAT-110")
    void tleWithoutANameLineIsRejected() {
        assertThatThrownBy(() -> reader.read(ISS, LINE_1 + "\n" + LINE_2))
                .isInstanceOf(InvalidTleException.class)
                .hasMessage("A TLE has three lines, a name and two element lines, but 2 were sent");
    }

    @Test
    @Requirement("CAT-110")
    void nameOfAHundredCharactersIsAcceptedAndOneMoreIsRejected() {
        assertThat(reader.read(ISS, "N".repeat(100) + "\n" + LINE_1 + "\n" + LINE_2).objectName()).hasSize(100);
        assertThatThrownBy(() -> reader.read(ISS, "N".repeat(101) + "\n" + LINE_1 + "\n" + LINE_2))
                .isInstanceOf(InvalidTleException.class)
                .hasMessage("The name line is longer than 100 characters");
    }

    @Test
    @Requirement("CAT-110")
    void wrongChecksumIsRejected() {
        final var corrupted = LINE_2.substring(0, 68) + "5";

        assertThatThrownBy(() -> reader.read(ISS, "ISS\n" + LINE_1 + "\n" + corrupted))
                .isInstanceOf(InvalidTleException.class)
                .hasMessageStartingWith("The element lines are not a valid TLE: ")
                .hasMessageContaining("checksum");
    }

    @Test
    @Requirement("CAT-110")
    void truncatedLineIsRejected() {
        assertThatThrownBy(() -> reader.read(ISS, "ISS\n" + LINE_1 + "\n" + LINE_2.substring(0, 68)))
                .isInstanceOf(InvalidTleException.class)
                .hasMessageStartingWith("The element lines are not in the TLE format");
    }

    @Test
    @Requirement("CAT-110")
    void tleForAnotherSatelliteIsRejected() {
        assertThatThrownBy(() -> reader.read(new NoradId(99999), "ISS\n" + LINE_1 + "\n" + LINE_2))
                .isInstanceOf(InvalidTleException.class)
                .hasMessage("The TLE is for NORAD ID 25544, not 99999");
    }

    @Test
    @Requirement("CAT-110")
    void tleWithoutInternationalDesignatorIsRejected() {
        final var line1 = withChecksum("1 25544U          26305.78918649  .00005925  00000-0  11503-3 0  999");

        assertThatThrownBy(() -> reader.read(ISS, "ISS\n" + line1 + "\n" + LINE_2))
                .isInstanceOf(InvalidTleException.class)
                .hasMessageStartingWith("The TLE has no usable international designator: ");
    }

    private static String withChecksum(String line) {
        final int sum = line.chars().map(c -> Character.isDigit(c) ? c - '0' : c == '-' ? 1 : 0).sum();
        return line + sum % 10;
    }
}
