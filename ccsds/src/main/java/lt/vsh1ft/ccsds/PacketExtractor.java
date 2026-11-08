package lt.vsh1ft.ccsds;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class PacketExtractor {

    private final Consumer<String> discarded;
    private byte[] partial = new byte[0];
    private boolean synced;
    private int expectedFrameCount = -1;

    public PacketExtractor(Consumer<String> discarded) {
        this.discarded = Objects.requireNonNull(discarded, "discarded");
    }

    public List<TmPacket> accept(TmFrame frame) {
        if (frame.idle()) {
            return List.of();
        }
        if (expectedFrameCount >= 0 && frame.virtualChannelFrameCount() != expectedFrameCount) {
            lose("frames before virtual channel frame count " + frame.virtualChannelFrameCount() + " are missing");
        }
        expectedFrameCount = (frame.virtualChannelFrameCount() + 1) & TmFrame.MAX_FRAME_COUNT;
        final var data = frame.dataField();
        final var packets = new ArrayList<TmPacket>();
        if (frame.firstHeaderPointer() == TmFrame.NO_PACKET_START) {
            if (synced) {
                partial = join(partial, data);
                extractLeading(packets);
            }
            return packets;
        }
        final var start = frame.firstHeaderPointer();
        if (synced) {
            partial = join(partial, Arrays.copyOf(data, start));
            extractLeading(packets);
            if (partial.length > 0) {
                lose(partial.length + " bytes before the first header pointer don't complete a packet");
            }
        }
        partial = Arrays.copyOfRange(data, start, data.length);
        synced = true;
        extractLeading(packets);
        return packets;
    }

    private void extractLeading(List<TmPacket> packets) {
        var offset = 0;
        while (partial.length - offset >= PrimaryHeader.LENGTH) {
            final PrimaryHeader header;
            try {
                header = PrimaryHeader.readFrom(ByteBuffer.wrap(partial, offset, PrimaryHeader.LENGTH));
            } catch (MalformedPacketException e) {
                lose("unreadable packet header: " + e.getMessage());
                return;
            }
            final var length = PrimaryHeader.LENGTH + header.dataFieldLength();
            if (partial.length - offset < length) {
                break;
            }
            final var bytes = Arrays.copyOfRange(partial, offset, offset + length);
            offset += length;
            if (!header.apid().equals(IdlePacket.APID)) {
                decode(bytes, packets);
            }
        }
        partial = Arrays.copyOfRange(partial, offset, partial.length);
    }

    private void decode(byte[] bytes, List<TmPacket> packets) {
        try {
            packets.add(TmPacket.decode(bytes));
        } catch (MalformedPacketException e) {
            discarded.accept("malformed packet: " + e.getMessage());
        }
    }

    private void lose(String reason) {
        if (partial.length > 0) {
            discarded.accept(reason);
        }
        partial = new byte[0];
        synced = false;
    }

    private static byte[] join(byte[] first, byte[] second) {
        final var joined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }
}
