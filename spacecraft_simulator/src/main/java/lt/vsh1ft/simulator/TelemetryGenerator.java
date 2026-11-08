package lt.vsh1ft.simulator;

import java.time.Instant;

import lt.vsh1ft.ccsds.Apid;
import lt.vsh1ft.ccsds.MessageType;
import lt.vsh1ft.ccsds.MissionTime;
import lt.vsh1ft.ccsds.PrimaryHeader;
import lt.vsh1ft.ccsds.PusTmSecondaryHeader;
import lt.vsh1ft.ccsds.TmPacket;

final class TelemetryGenerator {

    static final Apid PLATFORM_APID = new Apid(100);
    static final int GROUND_DESTINATION_ID = 0;
    private static final int CLOCK_SYNCHRONISED = 0;

    private final Spacecraft spacecraft;
    private final WrappingCounter sequenceCount = new WrappingCounter(PrimaryHeader.MAX_SEQUENCE_COUNT);
    private final WrappingCounter housekeepingCounter =
            new WrappingCounter(PusTmSecondaryHeader.MAX_MESSAGE_TYPE_COUNTER);

    TelemetryGenerator(Spacecraft spacecraft) {
        this.spacecraft = spacecraft;
    }

    TmPacket housekeeping(Instant now) {
        final var secondaryHeader = new PusTmSecondaryHeader(CLOCK_SYNCHRONISED,
                MessageType.HOUSEKEEPING_PARAMETER_REPORT, housekeepingCounter.next(), GROUND_DESTINATION_ID,
                MissionTime.toCuc(now));
        return new TmPacket(PLATFORM_APID, sequenceCount.next(), secondaryHeader,
                spacecraft.housekeeping(now).toUserData());
    }
}
