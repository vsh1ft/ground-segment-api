package lt.vsh1ft.ccsds;

import java.nio.ByteBuffer;
import java.util.Objects;

public record PrimaryHeader(PacketType type, boolean secondaryHeader, Apid apid, SequenceFlags sequenceFlags,
        int sequenceCount, int dataFieldLength) {

    public static final int LENGTH = 6;
    public static final int MAX_SEQUENCE_COUNT = 0x3FFF;
    public static final int MAX_DATA_FIELD_LENGTH = 0x10000;
    private static final int VERSION = 0;

    public PrimaryHeader {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(apid, "apid");
        Objects.requireNonNull(sequenceFlags, "sequenceFlags");
        Fields.requireRange("sequence count", sequenceCount, 0, MAX_SEQUENCE_COUNT);
        Fields.requireRange("packet data field length", dataFieldLength, 1, MAX_DATA_FIELD_LENGTH);
    }

    public void writeTo(ByteBuffer buffer) {
        final var secondaryHeaderFlag = secondaryHeader ? 1 : 0;
        buffer.putShort((short) (VERSION << 13 | type.ordinal() << 12 | secondaryHeaderFlag << 11 | apid.value()));
        buffer.putShort((short) (sequenceFlags.ordinal() << 14 | sequenceCount));
        buffer.putShort((short) (dataFieldLength - 1));
    }

    public static PrimaryHeader readFrom(ByteBuffer buffer) {
        if (buffer.remaining() < LENGTH) {
            throw new MalformedPacketException(
                    "a primary header needs " + LENGTH + " bytes, only " + buffer.remaining() + " left");
        }
        final var identification = Short.toUnsignedInt(buffer.getShort());
        final var sequenceControl = Short.toUnsignedInt(buffer.getShort());
        final var dataLength = Short.toUnsignedInt(buffer.getShort());
        final var version = identification >>> 13;
        if (version != VERSION) {
            throw new MalformedPacketException("unsupported packet version " + version);
        }
        return new PrimaryHeader(
                PacketType.values()[identification >>> 12 & 0x1],
                (identification >>> 11 & 0x1) == 1,
                new Apid(identification & Apid.MAX),
                SequenceFlags.values()[sequenceControl >>> 14],
                sequenceControl & MAX_SEQUENCE_COUNT,
                dataLength + 1);
    }
}
