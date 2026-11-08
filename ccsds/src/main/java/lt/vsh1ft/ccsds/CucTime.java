package lt.vsh1ft.ccsds;

import java.nio.ByteBuffer;

public record CucTime(long coarse, int fine) {

    public static final int LENGTH = 6;
    public static final long MAX_COARSE = 0xFFFF_FFFFL;
    public static final int MAX_FINE = 0xFFFF;

    public CucTime {
        Fields.requireRange("CUC coarse time", coarse, 0, MAX_COARSE);
        Fields.requireRange("CUC fine time", fine, 0, MAX_FINE);
    }

    public void writeTo(ByteBuffer buffer) {
        buffer.putInt((int) coarse);
        buffer.putShort((short) fine);
    }

    public static CucTime readFrom(ByteBuffer buffer) {
        return new CucTime(Integer.toUnsignedLong(buffer.getInt()), Short.toUnsignedInt(buffer.getShort()));
    }
}
