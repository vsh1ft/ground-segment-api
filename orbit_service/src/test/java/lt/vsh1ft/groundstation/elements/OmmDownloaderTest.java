package lt.vsh1ft.groundstation.elements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.shared.OrekitDataLoader;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OmmDownloaderTest {

    private static final NoradId ISS = new NoradId(25544);
    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

    private static final StoredOmm MALFORMED = new StoredOmm(null, 25544, "ISS (ZARYA)", "not-a-designator",
            OmmFixtures.ISS.epoch(), 15.49, 0.0007, 51.63, 339.17, 336.88, 23.18, 0, "U", 999, 59308,
            0.0001, 5.9e-5, 0, null);

    private final CelestrakClient client = mock(CelestrakClient.class);
    private final OmmDownloader downloader = new OmmDownloader(client, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeAll
    static void loadOrekitData() {
        new OrekitDataLoader(new File("orekit-data"));
    }

    @Test
    void downloadedOmmIsStampedWithTheDownloadTime() {
        given(client.latest(ISS)).willReturn(OmmFixtures.ISS);

        assertThat(downloader.download(ISS)).isEqualTo(OmmFixtures.ISS.withDownloadedAt(NOW));
    }

    @Test
    @Requirement("ELM-030")
    void ommThatCannotBeTurnedIntoATleIsRejected() {
        given(client.latest(ISS)).willReturn(MALFORMED);

        assertThatThrownBy(() -> downloader.download(ISS))
                .isInstanceOf(CelestrakException.class)
                .hasMessageStartingWith("Unusable OMM for 25544");
    }

    @Test
    void groupIsStampedWithTheDownloadTime() {
        given(client.group("stations")).willReturn(List.of(OmmFixtures.ISS));

        assertThat(downloader.downloadGroup("stations")).containsExactly(OmmFixtures.ISS.withDownloadedAt(NOW));
    }

    @Test
    @Requirement("ELM-030")
    void unusableOmmInAGroupIsSkippedAndTheRestKept() {
        given(client.group("stations")).willReturn(List.of(MALFORMED, OmmFixtures.ISS));

        assertThat(downloader.downloadGroup("stations")).containsExactly(OmmFixtures.ISS.withDownloadedAt(NOW));
    }
}
