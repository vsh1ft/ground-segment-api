package lt.vsh1ft.ccsds;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class IdlePacket {

    public static final Apid APID = new Apid(Apid.MAX);
    public static final int MIN_LENGTH = PrimaryHeader.LENGTH + 1;
    static final byte FILL = 0x55;

    private IdlePacket() {
    }

    public static byte[] of(int length) {
        Fields.requireRange("idle packet length", length, MIN_LENGTH,
                PrimaryHeader.LENGTH + PrimaryHeader.MAX_DATA_FIELD_LENGTH);
        final var buffer = ByteBuffer.allocate(length);
        new PrimaryHeader(PacketType.TELEMETRY, false, APID, SequenceFlags.UNSEGMENTED, 0,
                length - PrimaryHeader.LENGTH).writeTo(buffer);
        final var fill = new byte[length - PrimaryHeader.LENGTH];
        Arrays.fill(fill, FILL);
        return buffer.put(fill).array();
    }
}
