package lt.vsh1ft.xtce;

public record IntegerEncoding(int sizeInBits, boolean signed, boolean littleEndian) {

    public static final int MAX_SIZE_IN_BITS = 64;

    public IntegerEncoding {
        if (sizeInBits < 1 || sizeInBits > MAX_SIZE_IN_BITS) {
            throw new IllegalArgumentException("sizeInBits must be between 1 and " + MAX_SIZE_IN_BITS + ", was "
                    + sizeInBits);
        }
        if (littleEndian && sizeInBits % Byte.SIZE != 0) {
            throw new IllegalArgumentException("a little-endian integer must be whole bytes, was " + sizeInBits
                    + " bits");
        }
    }

    public IntegerEncoding(int sizeInBits, boolean signed) {
        this(sizeInBits, signed, false);
    }

    long decode(BitReader reader) {
        if (!littleEndian) {
            return reader.read(sizeInBits, signed);
        }
        long value = 0;
        for (int i = 0; i < sizeInBits / Byte.SIZE; i++) {
            value |= reader.read(Byte.SIZE, false) << (Byte.SIZE * i);
        }
        return BitReader.signExtend(value, sizeInBits, signed);
    }
}
