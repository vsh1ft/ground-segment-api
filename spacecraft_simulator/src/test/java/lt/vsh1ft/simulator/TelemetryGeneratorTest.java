package lt.vsh1ft.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import lt.vsh1ft.ccsds.Apid;
import lt.vsh1ft.ccsds.MessageType;
import lt.vsh1ft.ccsds.MissionTime;
import lt.vsh1ft.ccsds.TmPacket;
import org.junit.jupiter.api.Test;

class TelemetryGeneratorTest {

    private static final Instant START = Instant.parse("2026-11-08T00:00:00Z");
    private static final Duration ORBIT = Duration.ofMinutes(92);

    private final TelemetryGenerator generator = new TelemetryGenerator(new Spacecraft(START, ORBIT));

    @Test
    void quietStepSendsOnlyHousekeepingStampedWithMissionTime() {
        final var packets = generator.step(START);

        assertThat(packets).singleElement().satisfies(packet -> {
            assertThat(packet.apid()).isEqualTo(new Apid(100));
            assertThat(packet.secondaryHeader().messageType()).isEqualTo(MessageType.HOUSEKEEPING_PARAMETER_REPORT);
            assertThat(packet.secondaryHeader().destinationId()).isZero();
            assertThat(packet.secondaryHeader().time()).isEqualTo(MissionTime.toCuc(START));
            assertThat(packet.userData()).isEqualTo(new Spacecraft(START, ORBIT).step(START).housekeeping()
                    .toUserData());
        });
    }

    @Test
    void eventsAreSentBeforeTheHousekeepingOfTheSameStep() {
        final var lowBattery = START.plus(Duration.ofMinutes(69));

        final var packets = generator.step(lowBattery);

        assertThat(packets).extracting(packet -> packet.secondaryHeader().messageType()).containsExactly(
                MessageType.LOW_SEVERITY_ANOMALY_REPORT, MessageType.HIGH_SEVERITY_ANOMALY_REPORT,
                MessageType.HOUSEKEEPING_PARAMETER_REPORT);
        assertThat(HexFormat.of().formatHex(packets.get(0).userData())).isEqualTo("00011bbc");
        assertThat(HexFormat.of().formatHex(packets.get(1).userData())).isEqualTo("0005");
    }

    @Test
    void sequenceCountIsPerApidWhileMessageTypeCountersArePerServiceAndSubtype() {
        generator.step(START);
        final var packets = generator.step(START.plus(Duration.ofMinutes(69)));

        assertThat(packets).extracting(TmPacket::sequenceCount).containsExactly(1, 2, 3);
        assertThat(packets).extracting(packet -> packet.secondaryHeader().messageTypeCounter())
                .containsExactly(0, 0, 1);
    }

    @Test
    void generatedPacketsSurviveEncodingAndDecoding() {
        for (var packet : generator.step(START.plus(Duration.ofMinutes(69)))) {
            assertThat(TmPacket.decode(packet.encode())).isEqualTo(packet);
        }
    }
}
