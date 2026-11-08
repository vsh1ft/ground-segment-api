package lt.vsh1ft.ccsds;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public final class VirtualChannelFramer {

    private final int spacecraftId;
    private final int virtualChannel;
    private final int dataFieldLength;
    private final Deque<Long> packetStarts = new ArrayDeque<>();
    private byte[] pending = new byte[0];
    private long pendingStart;
    private int frameCount;

    public VirtualChannelFramer(int spacecraftId, int virtualChannel, int dataFieldLength) {
        Fields.requireRange("spacecraft ID", spacecraftId, 0, TmFrame.MAX_SPACECRAFT_ID);
        Fields.requireRange("virtual channel ID", virtualChannel, 0, TmFrame.MAX_VIRTUAL_CHANNEL);
        Fields.requireRange("data field length", dataFieldLength, 1, TmFrame.IDLE_DATA_ONLY);
        this.spacecraftId = spacecraftId;
        this.virtualChannel = virtualChannel;
        this.dataFieldLength = dataFieldLength;
    }

    public void add(byte[] packet) {
        packetStarts.add(pendingStart + pending.length);
        final var joined = new ByteArrayOutputStream(pending.length + packet.length);
        joined.writeBytes(pending);
        joined.writeBytes(packet);
        pending = joined.toByteArray();
    }

    public boolean isEmpty() {
        return pending.length == 0;
    }

    public boolean hasFullFrame() {
        return pending.length >= dataFieldLength;
    }

    public void clear() {
        pendingStart += pending.length;
        pending = new byte[0];
        packetStarts.clear();
    }

    public TmFrame fullFrame(int masterChannelFrameCount) {
        if (!hasFullFrame()) {
            throw new IllegalStateException("only " + pending.length + " of " + dataFieldLength + " bytes are waiting");
        }
        return take(masterChannelFrameCount);
    }

    public TmFrame paddedFrame(int masterChannelFrameCount) {
        if (isEmpty()) {
            throw new IllegalStateException("nothing is waiting on virtual channel " + virtualChannel);
        }
        if (!hasFullFrame()) {
            add(IdlePacket.of(Math.max(IdlePacket.MIN_LENGTH, dataFieldLength - pending.length)));
        }
        return take(masterChannelFrameCount);
    }

    public TmFrame idleFrame(int masterChannelFrameCount) {
        final var fill = new byte[dataFieldLength];
        Arrays.fill(fill, IdlePacket.FILL);
        return new TmFrame(spacecraftId, virtualChannel, masterChannelFrameCount, nextFrameCount(),
                TmFrame.IDLE_DATA_ONLY, fill);
    }

    private TmFrame take(int masterChannelFrameCount) {
        final var end = pendingStart + dataFieldLength;
        var firstHeaderPointer = TmFrame.NO_PACKET_START;
        while (!packetStarts.isEmpty() && packetStarts.peek() < end) {
            final long start = packetStarts.poll();
            if (firstHeaderPointer == TmFrame.NO_PACKET_START) {
                firstHeaderPointer = (int) (start - pendingStart);
            }
        }
        final var dataField = Arrays.copyOf(pending, dataFieldLength);
        pending = Arrays.copyOfRange(pending, dataFieldLength, pending.length);
        pendingStart = end;
        return new TmFrame(spacecraftId, virtualChannel, masterChannelFrameCount, nextFrameCount(),
                firstHeaderPointer, dataField);
    }

    private int nextFrameCount() {
        final var count = frameCount;
        frameCount = (frameCount + 1) & TmFrame.MAX_FRAME_COUNT;
        return count;
    }
}
