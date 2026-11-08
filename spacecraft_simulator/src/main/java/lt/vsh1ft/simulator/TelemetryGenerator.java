package lt.vsh1ft.simulator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lt.vsh1ft.ccsds.Apid;
import lt.vsh1ft.ccsds.MessageType;
import lt.vsh1ft.ccsds.MissionTime;
import lt.vsh1ft.ccsds.PrimaryHeader;
import lt.vsh1ft.ccsds.PusTmSecondaryHeader;
import lt.vsh1ft.ccsds.TmPacket;

final class TelemetryGenerator {

    private static final System.Logger LOG = System.getLogger(TelemetryGenerator.class.getName());

    static final Apid PLATFORM_APID = new Apid(100);
    static final int GROUND_DESTINATION_ID = 0;
    private static final int CLOCK_SYNCHRONISED = 0;

    private final Spacecraft spacecraft;
    private final WrappingCounter sequenceCount = new WrappingCounter(PrimaryHeader.MAX_SEQUENCE_COUNT);
    private final Map<MessageType, WrappingCounter> messageTypeCounters = new HashMap<>();

    TelemetryGenerator(Spacecraft spacecraft) {
        this.spacecraft = spacecraft;
    }

    List<TmPacket> step(Instant now) {
        final var step = spacecraft.step(now);
        final var packets = new ArrayList<TmPacket>();
        for (var event : step.events()) {
            final var report = event.definition().report();
            LOG.log(System.Logger.Level.INFO, "Event {0} as TM[{1},{2}]", event.definition(),
                    String.valueOf(report.serviceType()), String.valueOf(report.messageSubtype()));
            packets.add(packet(report, now, event.toUserData()));
        }
        packets.add(packet(MessageType.HOUSEKEEPING_PARAMETER_REPORT, now, step.housekeeping().toUserData()));
        return packets;
    }

    private TmPacket packet(MessageType type, Instant now, byte[] userData) {
        final var counter = messageTypeCounters.computeIfAbsent(type,
                ignored -> new WrappingCounter(PusTmSecondaryHeader.MAX_MESSAGE_TYPE_COUNTER));
        final var secondaryHeader = new PusTmSecondaryHeader(CLOCK_SYNCHRONISED, type, counter.next(),
                GROUND_DESTINATION_ID, MissionTime.toCuc(now));
        return new TmPacket(PLATFORM_APID, sequenceCount.next(), secondaryHeader, userData);
    }
}
