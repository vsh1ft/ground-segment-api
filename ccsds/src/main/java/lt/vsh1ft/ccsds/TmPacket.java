package lt.vsh1ft.ccsds;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

public record TmPacket(Apid apid, int sequenceCount, PusTmSecondaryHeader secondaryHeader, byte[] userData) {

    private static final int CRC_LENGTH = 2;
    public static final int MAX_USER_DATA_LENGTH =
            PrimaryHeader.MAX_DATA_FIELD_LENGTH - PusTmSecondaryHeader.LENGTH - CRC_LENGTH;

    public TmPacket {
        Objects.requireNonNull(apid, "apid");
        Objects.requireNonNull(secondaryHeader, "secondaryHeader");
        Fields.requireRange("sequence count", sequenceCount, 0, PrimaryHeader.MAX_SEQUENCE_COUNT);
        userData = userData.clone();
        Fields.requireRange("user data length", userData.length, 0, MAX_USER_DATA_LENGTH);
    }

    @Override
    public byte[] userData() {
        return userData.clone();
    }

    public byte[] encode() {
        final var dataFieldLength = PusTmSecondaryHeader.LENGTH + userData.length + CRC_LENGTH;
        final var buffer = ByteBuffer.allocate(PrimaryHeader.LENGTH + dataFieldLength);
        new PrimaryHeader(PacketType.TELEMETRY, true, apid, SequenceFlags.UNSEGMENTED, sequenceCount, dataFieldLength)
                .writeTo(buffer);
        secondaryHeader.writeTo(buffer);
        buffer.put(userData);
        buffer.putShort((short) Crc16.checksum(Arrays.copyOf(buffer.array(), buffer.position())));
        return buffer.array();
    }

    public static TmPacket decode(byte[] bytes) {
        final var buffer = ByteBuffer.wrap(bytes);
        final var header = PrimaryHeader.readFrom(buffer);
        if (header.type() != PacketType.TELEMETRY || !header.secondaryHeader()
                || header.sequenceFlags() != SequenceFlags.UNSEGMENTED) {
            throw new MalformedPacketException(
                    "expected an unsegmented telemetry packet with a secondary header, got " + header);
        }
        if (header.dataFieldLength() != buffer.remaining()) {
            throw new MalformedPacketException("the packet data length field says " + header.dataFieldLength()
                    + " bytes, but " + buffer.remaining() + " follow the primary header");
        }
        if (header.dataFieldLength() < PusTmSecondaryHeader.LENGTH + CRC_LENGTH) {
            throw new MalformedPacketException("a packet data field of " + header.dataFieldLength()
                    + " bytes is too short for a PUS TM secondary header and a CRC");
        }
        if (Crc16.checksum(bytes) != 0) {
            throw new MalformedPacketException("CRC mismatch");
        }
        final var secondaryHeader = PusTmSecondaryHeader.readFrom(buffer);
        final var userData = new byte[buffer.remaining() - CRC_LENGTH];
        buffer.get(userData);
        return new TmPacket(header.apid(), header.sequenceCount(), secondaryHeader, userData);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TmPacket packet
                && apid.equals(packet.apid)
                && sequenceCount == packet.sequenceCount
                && secondaryHeader.equals(packet.secondaryHeader)
                && Arrays.equals(userData, packet.userData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apid, sequenceCount, secondaryHeader, Arrays.hashCode(userData));
    }

    @Override
    public String toString() {
        return "TmPacket[apid=" + apid.value() + ", sequenceCount=" + sequenceCount + ", secondaryHeader="
                + secondaryHeader + ", userData=" + HexFormat.of().formatHex(userData) + "]";
    }
}
