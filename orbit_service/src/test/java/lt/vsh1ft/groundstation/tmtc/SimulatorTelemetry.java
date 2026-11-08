package lt.vsh1ft.groundstation.tmtc;

import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import lt.vsh1ft.ccsds.Apid;
import lt.vsh1ft.ccsds.MessageType;
import lt.vsh1ft.ccsds.MissionTime;
import lt.vsh1ft.ccsds.PusTmSecondaryHeader;
import lt.vsh1ft.ccsds.TmPacket;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

final class SimulatorTelemetry {

    static final NoradId SATELLITE = new NoradId(99999);
    static final TmPacket LIVE = TmPacket.decode(HexFormat.of()
            .parseHex("0864C0210014200319002100008182973AEC54011CF3009E01ACC1"));
    static final Instant LIVE_GENERATED = Instant.parse("2026-11-08T08:53:41.923156738Z");

    private SimulatorTelemetry() {
    }

    static TmLinkProperties link(Resource missionDatabase) {
        return new TmLinkProperties(SATELLITE.value(), "localhost", 10015, Duration.ofSeconds(2),
                Duration.ofSeconds(5), missionDatabase);
    }

    static MissionDatabases databases() {
        return new MissionDatabases(link(new ClassPathResource("mdb/simulator.xml")));
    }

    static TmPacket housekeeping(int sequenceCount, Instant generated, String userDataHex) {
        final var header = new PusTmSecondaryHeader(0, MessageType.HOUSEKEEPING_PARAMETER_REPORT, sequenceCount, 0,
                MissionTime.toCuc(generated));
        return new TmPacket(new Apid(100), sequenceCount, header, HexFormat.of().parseHex(userDataHex));
    }
}
