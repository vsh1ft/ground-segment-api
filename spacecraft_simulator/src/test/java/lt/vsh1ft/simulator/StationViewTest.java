package lt.vsh1ft.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StationViewTest {

    private static final String ISS_TLE = """
            ISS (ZARYA)
            1 25544U 98067A   26304.17644455  .00006183  00000-0  11976-3 0  9996
            2 25544  51.6332 347.1486 0007016 330.4977  29.5614 15.49124187592838
            """;
    private static final Instant AOS = Instant.parse("2026-10-31T23:04:53.190713Z");
    private static final Instant LOS = Instant.parse("2026-10-31T23:13:05.781483Z");

    @TempDir
    static Path files;

    private static StationView vilnius;

    @BeforeAll
    static void loadIssOverVilnius() throws IOException {
        vilnius = StationView.load(contact(Files.writeString(files.resolve("iss.tle"), ISS_TLE)));
    }

    @Test
    void satelliteIsInViewBetweenTheAcquisitionAndLossPredictedBySkyfield() {
        assertThat(vilnius.inView(AOS.minusSeconds(2))).isFalse();
        assertThat(vilnius.inView(AOS.plusSeconds(2))).isTrue();
        assertThat(vilnius.inView(Instant.parse("2026-10-31T23:09:00Z"))).isTrue();
        assertThat(vilnius.inView(LOS.minusSeconds(2))).isTrue();
        assertThat(vilnius.inView(LOS.plusSeconds(2))).isFalse();
    }

    @Test
    void orbitPeriodComesFromTheMeanMotion() {
        assertThat(vilnius.orbitPeriod()).isBetween(Duration.ofSeconds(5577), Duration.ofSeconds(5578));
    }

    @Test
    void twoLineTleWithoutANameIsAccepted() throws IOException {
        final var twoLines = Files.writeString(files.resolve("two.tle"), ISS_TLE.lines().skip(1)
                .reduce("", (text, line) -> text + line + "\n"));

        assertThat(StationView.load(contact(twoLines)).inView(Instant.parse("2026-10-31T23:09:00Z"))).isTrue();
    }

    @Test
    void fileWithoutATleIsRejected() throws IOException {
        final var empty = Files.writeString(files.resolve("empty.tle"), "\n  \n");

        assertThatIllegalArgumentException().isThrownBy(() -> StationView.load(contact(empty)))
                .withMessage(empty + " holds no TLE");
    }

    @Test
    void missingFileIsReportedWithItsPath() {
        final var missing = files.resolve("missing.tle");

        assertThatThrownBy(() -> StationView.load(contact(missing))).isInstanceOf(UncheckedIOException.class)
                .hasMessage("Can't read " + missing);
    }

    private static GroundContact contact(Path tleFile) {
        return new GroundContact(tleFile, 54.67519, 25.23422, 114.0, 5.0, Path.of("orekit-data"));
    }
}
