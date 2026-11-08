package lt.vsh1ft.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import lt.vsh1ft.ccsds.Apid;
import lt.vsh1ft.ccsds.MessageType;
import lt.vsh1ft.ccsds.MissionTime;
import lt.vsh1ft.ccsds.TmPacket;
import org.junit.jupiter.api.Test;

class TelemetryGeneratorTest {

    private static final Instant START = Instant.parse("2026-11-08T00:00:00Z");

    private final Spacecraft spacecraft = new Spacecraft(START);
    private final TelemetryGenerator generator = new TelemetryGenerator(spacecraft);

    @Test
    void housekeepingPacketCarriesTheSpacecraftReportStampedWithMissionTime() {
        final var packet = generator.housekeeping(START);

        assertThat(packet.apid()).isEqualTo(new Apid(100));
        assertThat(packet.secondaryHeader().messageType()).isEqualTo(MessageType.HOUSEKEEPING_PARAMETER_REPORT);
        assertThat(packet.secondaryHeader().destinationId()).isZero();
        assertThat(packet.secondaryHeader().time()).isEqualTo(MissionTime.toCuc(START));
        assertThat(packet.userData()).isEqualTo(spacecraft.housekeeping(START).toUserData());
    }

    @Test
    void eachPacketAdvancesTheSequenceCountAndTheMessageTypeCounter() {
        generator.housekeeping(START);
        final var second = generator.housekeeping(START.plusSeconds(1));

        assertThat(second.sequenceCount()).isEqualTo(1);
        assertThat(second.secondaryHeader().messageTypeCounter()).isEqualTo(1);
    }

    @Test
    void generatedPacketSurvivesEncodingAndDecoding() {
        final var packet = generator.housekeeping(START);

        assertThat(TmPacket.decode(packet.encode())).isEqualTo(packet);
    }
}
