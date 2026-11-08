package lt.vsh1ft.groundstation.satnogs;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;

class SatnogsFrameFileTest {

    private static final String BASE = "https://s3.eu-central-1.wasabisys.com/satnogs-network/data_obs/2026/11/8/18/"
            + "15386644/";

    @Test
    @Requirement("IF-080")
    void receptionTimeComesFromTheFileName() {
        assertThat(SatnogsFrameFile.receivedAt(BASE + "data_15386644_2026-11-08T19-03-34_3"))
                .hasValue(Instant.parse("2026-11-08T19:03:34Z"));
        assertThat(SatnogsFrameFile.receivedAt(BASE + "data_15386644_2026-11-08T19-02-36"))
                .hasValue(Instant.parse("2026-11-08T19:02:36Z"));
    }

    @Test
    @Requirement("IF-080")
    void fileNameWithoutATimeHasNoReceptionTime() {
        assertThat(SatnogsFrameFile.receivedAt(BASE + "waterfall_15386644.png")).isEmpty();
    }

    @Test
    @Requirement("IF-080")
    void rawBytesAreTheFrameAndHexTextIsDecoded() {
        final var raw = new byte[] {(byte) 0xC2, (byte) 0xA2, (byte) 0x8A, 0x00};

        assertThat(SatnogsFrameFile.frame(raw)).isEqualTo(raw);
        assertThat(SatnogsFrameFile.frame("c2a28a00\n".getBytes(StandardCharsets.US_ASCII))).isEqualTo(raw);
        assertThat(SatnogsFrameFile.frame("C2A28A0".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo("C2A28A0".getBytes(StandardCharsets.US_ASCII));
    }
}
