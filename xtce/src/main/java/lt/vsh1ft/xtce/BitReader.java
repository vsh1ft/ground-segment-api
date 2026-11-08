package lt.vsh1ft.xtce;

final class BitReader {

    private final byte[] bytes;
    private int position;

    BitReader(byte[] bytes) {
        this.bytes = bytes;
    }

    long read(int sizeInBits, boolean signed) {
        require(sizeInBits);
        long value = 0;
        for (int i = 0; i < sizeInBits; i++) {
            final int bit = (bytes[position >>> 3] >>> (7 - (position & 7))) & 1;
            value = (value << 1) | bit;
            position++;
        }
        return signExtend(value, sizeInBits, signed);
    }

    void skip(long sizeInBits) {
        require(sizeInBits);
        position += (int) sizeInBits;
    }

    static long signExtend(long value, int sizeInBits, boolean signed) {
        if (!signed || sizeInBits == Long.SIZE) {
            return value;
        }
        final boolean negative = (value >>> (sizeInBits - 1)) == 1;
        return negative ? value - (1L << sizeInBits) : value;
    }

    private void require(long sizeInBits) {
        if (position + sizeInBits > bytes.length * 8L) {
            throw new PacketDecodingException("Packet has " + bytes.length * 8 + " bits, needed " + sizeInBits
                    + " more from bit " + position);
        }
    }
}
