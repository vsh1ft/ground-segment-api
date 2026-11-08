package lt.vsh1ft.ccsds;

public final class Crc16 {

    private static final int POLYNOMIAL = 0x1021;
    private static final int INITIAL_VALUE = 0xFFFF;
    private static final int TOP_BIT = 0x8000;
    private static final int SIXTEEN_BITS = 0xFFFF;

    private Crc16() {
    }

    public static int checksum(byte[] data) {
        var crc = INITIAL_VALUE;
        for (final var b : data) {
            crc ^= (b & 0xFF) << 8;
            for (var bit = 0; bit < Byte.SIZE; bit++) {
                crc = (crc & TOP_BIT) != 0 ? (crc << 1) ^ POLYNOMIAL : crc << 1;
            }
            crc &= SIXTEEN_BITS;
        }
        return crc;
    }
}
