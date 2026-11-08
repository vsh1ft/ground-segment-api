package lt.vsh1ft.ccsds;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class PacketExtractorTest {

    private static final int DATA = 56;

    private final List<String> discarded = new ArrayList<>();
    private final PacketExtractor extractor = new PacketExtractor(discarded::add);
    private final VirtualChannelFramer framer = new VirtualChannelFramer(42, 0, DATA);

    @Test
    void packetsSpanningFramesAreJoinedAndIdlePacketsDropped() {
        final var sent = packets(7);
        sent.forEach(packet -> framer.add(packet.encode()));

        final var received = new ArrayList<TmPacket>();
        while (framer.hasFullFrame()) {
            received.addAll(extractor.accept(framer.fullFrame(0)));
        }
        received.addAll(extractor.accept(framer.paddedFrame(0)));

        assertThat(received).isEqualTo(sent);
        assertThat(discarded).isEmpty();
    }

    @Test
    void lostFrameCostsOnlyThePacketsItCarried() {
        final var sent = packets(7);
        sent.forEach(packet -> framer.add(packet.encode()));
        final var first = framer.fullFrame(0);
        framer.fullFrame(1);
        final var third = framer.fullFrame(2);

        final var received = new ArrayList<>(extractor.accept(first));
        received.addAll(extractor.accept(third));

        assertThat(third.firstHeaderPointer()).isEqualTo(23);
        assertThat(received).containsExactly(sent.get(0), sent.get(1), sent.get(5));
        assertThat(discarded).containsExactly("frames before virtual channel frame count 2 are missing");
    }

    @Test
    void receptionStartingMidStreamBeginsAtTheFirstHeaderPointer() {
        final var sent = packets(5);
        sent.forEach(packet -> framer.add(packet.encode()));
        framer.fullFrame(0);

        assertThat(extractor.accept(framer.fullFrame(1))).containsExactly(sent.get(3));
        assertThat(discarded).isEmpty();
    }

    @Test
    void frameWithoutAPacketStartIsSkippedUntilOneStarts() {
        final var longPacket = packet(0, 150);
        framer.add(longPacket.encode());
        framer.add(packet(1, 6).encode());
        framer.fullFrame(0);
        final var middle = framer.fullFrame(1);

        assertThat(extractor.accept(middle)).isEmpty();
        assertThat(extractor.accept(framer.fullFrame(2))).isEmpty();
        assertThat(extractor.accept(framer.paddedFrame(3))).containsExactly(packet(1, 6));
    }

    @Test
    void packetLongerThanTwoFramesIsJoinedFromThreeFrames() {
        final var longPacket = packet(0, 150);
        framer.add(longPacket.encode());

        final var received = new ArrayList<TmPacket>();
        var frames = 0;
        while (framer.hasFullFrame()) {
            received.addAll(extractor.accept(framer.fullFrame(frames++)));
        }
        received.addAll(extractor.accept(framer.paddedFrame(frames++)));

        assertThat(frames).isEqualTo(4);
        assertThat(received).containsExactly(longPacket);
    }

    @Test
    void idleFramesAreIgnoredAndDoNotBreakTheSequence() {
        final var idle = new VirtualChannelFramer(42, 7, DATA);
        framer.add(packet(0, 40).encode());

        assertThat(extractor.accept(idle.idleFrame(0))).isEmpty();
        assertThat(extractor.accept(framer.fullFrame(1))).isEmpty();
        assertThat(extractor.accept(idle.idleFrame(2))).isEmpty();
        assertThat(extractor.accept(framer.paddedFrame(3))).containsExactly(packet(0, 40));
    }

    @Test
    void frameCountWrappingFrom255To0IsNotAGap() {
        for (var i = 0; i < 255; i++) {
            extractor.accept(new TmFrame(42, 0, 0, i, TmFrame.NO_PACKET_START, new byte[DATA]));
        }
        final var afterWrap = framerFrame(packet(0, 20), 0);

        assertThat(extractor.accept(new TmFrame(42, 0, 0, 255, TmFrame.NO_PACKET_START, new byte[DATA]))).isEmpty();
        assertThat(extractor.accept(afterWrap)).containsExactly(packet(0, 20));
        assertThat(discarded).isEmpty();
    }

    @Test
    void bytesThatDoNotEndWhereTheFirstHeaderPointerSaysAreDiscarded() {
        final var c = packet(2, 6).encode();
        final var first = extractor.accept(frame(0, 0, packet(0, 6).encode(), packet(1, 6).encode(),
                Arrays.copyOf(c, 2)));

        final var second = extractor.accept(frame(1, 30, Arrays.copyOfRange(c, 2, 27), new byte[5],
                IdlePacket.of(26)));

        assertThat(first).containsExactly(packet(0, 6), packet(1, 6));
        assertThat(second).containsExactly(packet(2, 6));
        assertThat(discarded).containsExactly("5 bytes before the first header pointer don't complete a packet");
    }

    @Test
    void unreadablePacketHeaderDropsTheRestUntilTheNextPacketStart() {
        final var data = new byte[DATA];
        data[0] = (byte) 0xE0;

        assertThat(extractor.accept(new TmFrame(42, 0, 0, 0, 0, data))).isEmpty();
        assertThat(discarded).containsExactly("unreadable packet header: unsupported packet version 7");
        assertThat(extractor.accept(new TmFrame(42, 0, 0, 1, TmFrame.NO_PACKET_START, new byte[DATA]))).isEmpty();
    }

    @Test
    void packetFailingItsCrcIsDiscardedAndTheNextOneKept() {
        final var damaged = packet(0, 2).encode();
        damaged[damaged.length - 1] ^= 1;
        framer.add(damaged);
        framer.add(packet(1, 2).encode());

        assertThat(extractor.accept(framer.paddedFrame(0))).containsExactly(packet(1, 2));
        assertThat(discarded).containsExactly("malformed packet: CRC mismatch");
    }

    @Test
    void gapWithNothingWaitingDiscardsNothing() {
        extractor.accept(frame(0, 0, IdlePacket.of(DATA)));

        assertThat(extractor.accept(frame(5, 0, packet(2, 10).encode(), IdlePacket.of(DATA - 31))))
                .containsExactly(packet(2, 10));
        assertThat(discarded).isEmpty();
    }

    private TmFrame framerFrame(TmPacket packet, int masterCount) {
        framer.add(packet.encode());
        return framer.paddedFrame(masterCount);
    }

    private static TmFrame frame(int virtualCount, int firstHeaderPointer, byte[]... parts) {
        final var data = ByteBuffer.allocate(DATA);
        for (final var part : parts) {
            data.put(part);
        }
        return new TmFrame(42, 0, 0, virtualCount, firstHeaderPointer, data.array());
    }

    private static List<TmPacket> packets(int count) {
        final var packets = new ArrayList<TmPacket>();
        for (var i = 0; i < count; i++) {
            packets.add(packet(i, 6));
        }
        return packets;
    }

    private static TmPacket packet(int sequenceCount, int userDataLength) {
        final var header = new PusTmSecondaryHeader(0, MessageType.HOUSEKEEPING_PARAMETER_REPORT, sequenceCount, 0,
                new CucTime(sequenceCount, 0));
        return new TmPacket(new Apid(100), sequenceCount, header, new byte[userDataLength]);
    }
}
