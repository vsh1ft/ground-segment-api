package lt.vsh1ft.ccsds;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

public record TmFrame(int spacecraftId, int virtualChannel, int masterChannelFrameCount,
                      int virtualChannelFrameCount, int firstHeaderPointer, byte[] dataField) {

    public static final int HEADER_LENGTH = 6;
    public static final int FECF_LENGTH = 2;
    public static final int MAX_SPACECRAFT_ID = 0x3FF;
    public static final int MAX_VIRTUAL_CHANNEL = 7;
    public static final int MAX_FRAME_COUNT = 0xFF;
    public static final int NO_PACKET_START = 0x7FF;
    public static final int IDLE_DATA_ONLY = 0x7FE;
    private static final int VERSION = 0;
    private static final int SEGMENT_LENGTH_ID = 0b11;
    private static final int MAX_DATA_FIELD_LENGTH = IDLE_DATA_ONLY;

    public TmFrame {
        Fields.requireRange("spacecraft ID", spacecraftId, 0, MAX_SPACECRAFT_ID);
        Fields.requireRange("virtual channel ID", virtualChannel, 0, MAX_VIRTUAL_CHANNEL);
        Fields.requireRange("master channel frame count", masterChannelFrameCount, 0, MAX_FRAME_COUNT);
        Fields.requireRange("virtual channel frame count", virtualChannelFrameCount, 0, MAX_FRAME_COUNT);
        Objects.requireNonNull(dataField, "dataField");
        dataField = dataField.clone();
        Fields.requireRange("data field length", dataField.length, 1, MAX_DATA_FIELD_LENGTH);
        if (firstHeaderPointer != NO_PACKET_START && firstHeaderPointer != IDLE_DATA_ONLY) {
            Fields.requireRange("first header pointer", firstHeaderPointer, 0, dataField.length - 1);
        }
    }

    public static int length(int dataFieldLength) {
        return HEADER_LENGTH + dataFieldLength + FECF_LENGTH;
    }

    @Override
    public byte[] dataField() {
        return dataField.clone();
    }

    public boolean idle() {
        return firstHeaderPointer == IDLE_DATA_ONLY;
    }

    public byte[] encode() {
        final var buffer = ByteBuffer.allocate(length(dataField.length));
        buffer.putShort((short) (VERSION << 14 | spacecraftId << 4 | virtualChannel << 1));
        buffer.put((byte) masterChannelFrameCount);
        buffer.put((byte) virtualChannelFrameCount);
        buffer.putShort((short) (SEGMENT_LENGTH_ID << 11 | firstHeaderPointer));
        buffer.put(dataField);
        buffer.putShort((short) Crc16.checksum(Arrays.copyOf(buffer.array(), buffer.position())));
        return buffer.array();
    }

    public static TmFrame decode(byte[] frame) {
        if (frame.length < length(1)) {
            throw new MalformedFrameException("a frame needs at least " + length(1) + " bytes, got " + frame.length);
        }
        if (Crc16.checksum(frame) != 0) {
            throw new MalformedFrameException("frame error control mismatch");
        }
        final var buffer = ByteBuffer.wrap(frame);
        final var identification = Short.toUnsignedInt(buffer.getShort());
        final var masterChannelFrameCount = Byte.toUnsignedInt(buffer.get());
        final var virtualChannelFrameCount = Byte.toUnsignedInt(buffer.get());
        final var dataFieldStatus = Short.toUnsignedInt(buffer.getShort());
        final var version = identification >>> 14;
        if (version != VERSION) {
            throw new MalformedFrameException("unsupported transfer frame version " + version);
        }
        if ((identification & 1) != 0 || dataFieldStatus >>> 11 != SEGMENT_LENGTH_ID) {
            throw new MalformedFrameException(
                    "expected a frame without OCF or secondary header carrying packets, got data field status "
                            + Integer.toBinaryString(dataFieldStatus));
        }
        final var dataField = Arrays.copyOfRange(frame, HEADER_LENGTH, frame.length - FECF_LENGTH);
        try {
            return new TmFrame(identification >>> 4 & MAX_SPACECRAFT_ID, identification >>> 1 & MAX_VIRTUAL_CHANNEL,
                    masterChannelFrameCount, virtualChannelFrameCount, dataFieldStatus & NO_PACKET_START, dataField);
        } catch (IllegalArgumentException e) {
            throw new MalformedFrameException(e.getMessage());
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TmFrame frame
                && spacecraftId == frame.spacecraftId
                && virtualChannel == frame.virtualChannel
                && masterChannelFrameCount == frame.masterChannelFrameCount
                && virtualChannelFrameCount == frame.virtualChannelFrameCount
                && firstHeaderPointer == frame.firstHeaderPointer
                && Arrays.equals(dataField, frame.dataField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spacecraftId, virtualChannel, masterChannelFrameCount, virtualChannelFrameCount,
                firstHeaderPointer, Arrays.hashCode(dataField));
    }

    @Override
    public String toString() {
        return "TmFrame[spacecraftId=" + spacecraftId + ", virtualChannel=" + virtualChannel
                + ", masterChannelFrameCount=" + masterChannelFrameCount + ", virtualChannelFrameCount="
                + virtualChannelFrameCount + ", firstHeaderPointer=" + firstHeaderPointer + ", dataField="
                + HexFormat.of().formatHex(dataField) + "]";
    }
}
