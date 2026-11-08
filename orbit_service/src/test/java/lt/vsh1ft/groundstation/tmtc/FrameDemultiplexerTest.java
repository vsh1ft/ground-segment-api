package lt.vsh1ft.groundstation.tmtc;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;

import lt.vsh1ft.ccsds.Apid;
import lt.vsh1ft.ccsds.CucTime;
import lt.vsh1ft.ccsds.MessageType;
import lt.vsh1ft.ccsds.PusTmSecondaryHeader;
import lt.vsh1ft.ccsds.TmPacket;
import lt.vsh1ft.ccsds.VirtualChannelFramer;
import lt.vsh1ft.groundstation.contact.ContactWatch;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;

class FrameDemultiplexerTest {

    private static final NoradId SATELLITE = new NoradId(99999);
    private static final Instant T0 = Instant.parse("2026-11-08T18:03:00Z");
    private static final TmPacket REFERENCE = TmPacket.decode(TmLinkTest.REFERENCE);

    private final TelemetryReception reception = mock(TelemetryReception.class);
    private final ContactWatch contacts = mock(ContactWatch.class);
    private final FrameDemultiplexer frames = new FrameDemultiplexer(SATELLITE, 42, reception, contacts);
    private final VirtualChannelFramer housekeeping = new VirtualChannelFramer(42, 0, 56);
    private final VirtualChannelFramer events = new VirtualChannelFramer(42, 1, 56);
    private final VirtualChannelFramer idle = new VirtualChannelFramer(42, 7, 56);

    @Test
    @Requirement("TM-060")
    void packetsOfEachVirtualChannelAreJoinedAndReceivedWithTheFrameReceptionTime() {
        housekeeping.add(packet(1, 30));
        housekeeping.add(packet(2, 30));
        events.add(packet(3, 2));

        frames.receive(housekeeping.fullFrame(0).encode(), T0);
        frames.receive(events.paddedFrame(1).encode(), T0.plusMillis(250));
        frames.receive(housekeeping.paddedFrame(2).encode(), T0.plusMillis(500));

        final var order = inOrder(reception);
        order.verify(reception).receive(SATELLITE, TmPacket.decode(packet(1, 30)), T0);
        order.verify(reception).receive(SATELLITE, TmPacket.decode(packet(3, 2)), T0.plusMillis(250));
        order.verify(reception).receive(SATELLITE, TmPacket.decode(packet(2, 30)), T0.plusMillis(500));
    }

    @Test
    @Requirement("TM-060")
    void idleFrameIsCountedButCarriesNoPackets() {
        frames.receive(idle.idleFrame(0).encode(), T0);

        verify(contacts).frameReceived(SATELLITE, T0, 0, true);
        verifyNoInteractions(reception);
    }

    @Test
    @Requirement("TM-060")
    void frameOfAnotherSpacecraftIsDroppedUncounted() {
        frames.receive(new VirtualChannelFramer(43, 7, 56).idleFrame(0).encode(), T0);

        verifyNoInteractions(contacts, reception);
    }

    @Test
    @Requirement("TM-030")
    void frameFailingTheErrorControlIsCountedAndDropped() {
        final var damaged = idle.idleFrame(0).encode();
        damaged[10] ^= 1;

        frames.receive(damaged, T0);

        verify(contacts).frameFailedCheck(SATELLITE, T0);
        verify(contacts, never()).frameReceived(eq(SATELLITE), eq(T0), anyInt(), anyBoolean());
        verifyNoInteractions(reception);
    }

    @Test
    @Requirement("CON-040")
    void gapInTheMasterChannelFrameCountIsCountedAsLostFrames() {
        frames.receive(idle.idleFrame(17).encode(), T0);
        frames.receive(idle.idleFrame(18).encode(), T0.plusMillis(250));
        frames.receive(idle.idleFrame(23).encode(), T0.plusSeconds(2));

        verify(contacts).frameReceived(SATELLITE, T0, 0, true);
        verify(contacts).frameReceived(SATELLITE, T0.plusMillis(250), 0, true);
        verify(contacts).frameReceived(SATELLITE, T0.plusSeconds(2), 4, true);
    }

    @Test
    @Requirement("CON-040")
    void gapAcrossTheWrapOfTheCountIsComputedModulo256() {
        frames.receive(idle.idleFrame(254).encode(), T0);
        frames.receive(idle.idleFrame(1).encode(), T0.plusSeconds(1));
        frames.receive(idle.idleFrame(2).encode(), T0.plusSeconds(2));

        verify(contacts).frameReceived(SATELLITE, T0.plusSeconds(1), 2, true);
        verify(contacts).frameReceived(SATELLITE, T0.plusSeconds(2), 0, true);
    }

    @Test
    @Requirement("CON-040")
    void gapIsCountedOnlyWhenThePreviousFrameArrivedLessThan30SecondsEarlier() {
        final var justInTime = T0.plus(FrameDemultiplexer.COUNTED_GAP).minusNanos(1);
        final var tooLate = justInTime.plus(FrameDemultiplexer.COUNTED_GAP);

        frames.receive(idle.idleFrame(10).encode(), T0);
        frames.receive(idle.idleFrame(20).encode(), justInTime);
        frames.receive(idle.idleFrame(30).encode(), tooLate);

        verify(contacts).frameReceived(SATELLITE, justInTime, 9, true);
        verify(contacts).frameReceived(SATELLITE, tooLate, 0, true);
    }

    @Test
    @Requirement("TM-060")
    void lostFrameCostsThePacketItCarriedAndTheNextOnesArrive() {
        for (var sequence = 0; sequence < 6; sequence++) {
            housekeeping.add(packet(sequence, 6));
        }
        frames.receive(housekeeping.fullFrame(0).encode(), T0);
        housekeeping.fullFrame(1);
        frames.receive(housekeeping.paddedFrame(2).encode(), T0.plusSeconds(1));

        verify(reception).receive(SATELLITE, TmPacket.decode(packet(0, 6)), T0);
        verify(reception).receive(SATELLITE, TmPacket.decode(packet(1, 6)), T0);
        verify(reception).receive(SATELLITE, TmPacket.decode(packet(5, 6)), T0.plusSeconds(1));
        verify(reception, never()).receive(SATELLITE, TmPacket.decode(packet(3, 6)), T0.plusSeconds(1));
        verify(contacts).frameReceived(SATELLITE, T0.plusSeconds(1), 1, false);
    }

    @Test
    @Requirement({"IF-060", "IF-040"})
    void referencePacketTravelsInOneFrame() {
        housekeeping.add(TmLinkTest.REFERENCE);

        frames.receive(housekeeping.paddedFrame(0).encode(), T0);

        verify(reception).receive(SATELLITE, REFERENCE, T0);
        verify(contacts).frameReceived(SATELLITE, T0, 0, false);
    }

    private static byte[] packet(int sequenceCount, int userDataLength) {
        final var header = new PusTmSecondaryHeader(0, MessageType.HOUSEKEEPING_PARAMETER_REPORT, sequenceCount, 0,
                new CucTime(2_172_787_237L + sequenceCount, 0));
        return new TmPacket(new Apid(100), sequenceCount, header, new byte[userDataLength]).encode();
    }
}
