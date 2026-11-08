package lt.vsh1ft.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lt.vsh1ft.ccsds.MissionTime;
import lt.vsh1ft.ccsds.TmPacket;
import org.junit.jupiter.api.Test;

class DownlinkTest {

    private static final Instant START = Instant.parse("2026-11-08T00:00:00Z");
    private static final Instant AOS = START.plusSeconds(10);
    private static final Instant LOS = START.plusSeconds(20);

    private final List<byte[]> sent = new ArrayList<>();
    private final Downlink downlink = new Downlink(new TelemetryGenerator(new Spacecraft(START,
            Duration.ofMinutes(92))), at -> !at.isBefore(AOS) && at.isBefore(LOS), sent::add);

    @Test
    void packetsAreSentOnlyWhileTheStationSeesTheSpacecraft() {
        for (int second = 0; second < 30; second++) {
            downlink.step(START.plusSeconds(second));
        }

        assertThat(sent).hasSize(10);
        assertThat(TmPacket.decode(sent.getFirst()).secondaryHeader().time())
                .isEqualTo(MissionTime.toCuc(AOS));
    }

    @Test
    void packetsMadeOutOfViewStillAdvanceTheSequenceCount() {
        for (int second = 0; second < 11; second++) {
            downlink.step(START.plusSeconds(second));
        }

        assertThat(TmPacket.decode(sent.getLast()).sequenceCount()).isEqualTo(10);
    }
}
