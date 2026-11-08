package lt.vsh1ft.ccsds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class VirtualChannelFramerTest {

    private static final int DATA = 56;

    private final VirtualChannelFramer framer = new VirtualChannelFramer(42, 0, DATA);

    @Test
    void housekeepingPacketsFillFramesAndTheFirstHeaderPointerShowsWhereOneStarts() {
        for (var i = 0; i < 5; i++) {
            framer.add(packet(27, i));
        }

        final var first = framer.fullFrame(10);
        final var second = framer.fullFrame(11);

        assertThat(first.firstHeaderPointer()).isZero();
        assertThat(second.firstHeaderPointer()).isEqualTo(25);
        assertThat(first.virtualChannelFrameCount()).isZero();
        assertThat(second.virtualChannelFrameCount()).isEqualTo(1);
        assertThat(second.masterChannelFrameCount()).isEqualTo(11);
        assertThat(second.spacecraftId()).isEqualTo(42);
        assertThat(second.virtualChannel()).isZero();
        assertThat(framer.hasFullFrame()).isFalse();
        assertThat(framer.isEmpty()).isFalse();
    }

    @Test
    void packetLongerThanADataFieldLeavesAFrameWithoutAPacketStart() {
        framer.add(packet(130, 0));

        assertThat(framer.fullFrame(0).firstHeaderPointer()).isZero();
        assertThat(framer.fullFrame(1).firstHeaderPointer()).isEqualTo(TmFrame.NO_PACKET_START);
        assertThat(framer.hasFullFrame()).isFalse();
    }

    @Test
    void paddedFrameEndsWithAnIdlePacket() {
        framer.add(packet(23, 0));

        final var frame = framer.paddedFrame(0);

        assertThat(frame.firstHeaderPointer()).isZero();
        final var idle = PrimaryHeader.readFrom(ByteBuffer.wrap(frame.dataField(), 23, 6));
        assertThat(idle.apid()).isEqualTo(IdlePacket.APID);
        assertThat(idle.dataFieldLength()).isEqualTo(DATA - 23 - 6);
        assertThat(framer.isEmpty()).isTrue();
    }

    @Test
    void idlePacketThatDoesNotFitContinuesInTheNextFrame() {
        framer.add(packet(52, 0));

        final var first = framer.paddedFrame(0);
        final var second = framer.paddedFrame(1);

        assertThat(first.firstHeaderPointer()).isZero();
        assertThat(second.firstHeaderPointer()).isEqualTo(3);
        assertThat(framer.isEmpty()).isTrue();
    }

    @Test
    void fullFramePaddedFrameNeedsNoIdlePacket() {
        framer.add(packet(DATA, 0));

        assertThat(framer.paddedFrame(0).dataField()).isEqualTo(packet(DATA, 0));
        assertThat(framer.isEmpty()).isTrue();
    }

    @Test
    void clearedChannelForgetsWaitingBytesButKeepsCounting() {
        framer.add(packet(40, 0));
        framer.clear();
        framer.add(packet(DATA, 1));

        final var frame = framer.fullFrame(5);

        assertThat(frame.firstHeaderPointer()).isZero();
        assertThat(frame.dataField()).isEqualTo(packet(DATA, 1));
        assertThat(frame.virtualChannelFrameCount()).isZero();
    }

    @Test
    void idleFrameHoldsOnlyFillAndCountsOnItsOwnChannel() {
        final var idleChannel = new VirtualChannelFramer(42, 7, DATA);

        final var frame = idleChannel.idleFrame(3);

        assertThat(frame.idle()).isTrue();
        assertThat(frame.virtualChannel()).isEqualTo(7);
        assertThat(frame.dataField()).containsOnly(0x55);
        assertThat(idleChannel.idleFrame(4).virtualChannelFrameCount()).isEqualTo(1);
    }

    @Test
    void frameCountWrapsAfter255() {
        for (var i = 0; i < 256; i++) {
            framer.idleFrame(0);
        }

        assertThat(framer.idleFrame(0).virtualChannelFrameCount()).isZero();
    }

    @Test
    void frameCannotBeTakenBeforeItsBytesAreWaiting() {
        framer.add(packet(30, 0));

        assertThatIllegalStateException().isThrownBy(() -> framer.fullFrame(0))
                .withMessage("only 30 of 56 bytes are waiting");
        assertThatIllegalStateException().isThrownBy(() -> new VirtualChannelFramer(42, 1, DATA).paddedFrame(0))
                .withMessage("nothing is waiting on virtual channel 1");
    }

    @Test
    void channelIdentifiersAndDataFieldLengthAreChecked() {
        assertThatIllegalArgumentException().isThrownBy(() -> new VirtualChannelFramer(1024, 0, DATA));
        assertThatIllegalArgumentException().isThrownBy(() -> new VirtualChannelFramer(0, 8, DATA));
        assertThatIllegalArgumentException().isThrownBy(() -> new VirtualChannelFramer(0, 0, 0));
        assertThat(new VirtualChannelFramer(1023, 7, 2046).isEmpty()).isTrue();
    }

    static byte[] packet(int length, int fill) {
        final var bytes = new byte[length];
        Arrays.fill(bytes, (byte) fill);
        return bytes;
    }
}
