package lt.vsh1ft.ccsds;

import java.nio.ByteBuffer;
import java.util.Objects;

public record PusTmSecondaryHeader(int timeReferenceStatus, MessageType messageType, int messageTypeCounter,
        int destinationId, CucTime time) {

    public static final int LENGTH = 7 + CucTime.LENGTH;
    public static final int MAX_TIME_REFERENCE_STATUS = 0xF;
    public static final int MAX_MESSAGE_TYPE_COUNTER = 0xFFFF;
    public static final int MAX_DESTINATION_ID = 0xFFFF;
    private static final int PUS_VERSION = 2;

    public PusTmSecondaryHeader {
        Objects.requireNonNull(messageType, "messageType");
        Objects.requireNonNull(time, "time");
        Fields.requireRange("spacecraft time reference status", timeReferenceStatus, 0, MAX_TIME_REFERENCE_STATUS);
        Fields.requireRange("message type counter", messageTypeCounter, 0, MAX_MESSAGE_TYPE_COUNTER);
        Fields.requireRange("destination ID", destinationId, 0, MAX_DESTINATION_ID);
    }

    public void writeTo(ByteBuffer buffer) {
        buffer.put((byte) (PUS_VERSION << 4 | timeReferenceStatus));
        buffer.put((byte) messageType.serviceType());
        buffer.put((byte) messageType.messageSubtype());
        buffer.putShort((short) messageTypeCounter);
        buffer.putShort((short) destinationId);
        time.writeTo(buffer);
    }

    public static PusTmSecondaryHeader readFrom(ByteBuffer buffer) {
        if (buffer.remaining() < LENGTH) {
            throw new MalformedPacketException(
                    "a PUS TM secondary header needs " + LENGTH + " bytes, only " + buffer.remaining() + " left");
        }
        final var versionAndStatus = Byte.toUnsignedInt(buffer.get());
        final var version = versionAndStatus >>> 4;
        if (version != PUS_VERSION) {
            throw new MalformedPacketException("unsupported PUS version " + version);
        }
        return new PusTmSecondaryHeader(
                versionAndStatus & MAX_TIME_REFERENCE_STATUS,
                new MessageType(Byte.toUnsignedInt(buffer.get()), Byte.toUnsignedInt(buffer.get())),
                Short.toUnsignedInt(buffer.getShort()),
                Short.toUnsignedInt(buffer.getShort()),
                CucTime.readFrom(buffer));
    }
}
