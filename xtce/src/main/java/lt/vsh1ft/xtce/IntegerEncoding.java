package lt.vsh1ft.xtce;

public record IntegerEncoding(int sizeInBits, boolean signed) {

    public static final int MAX_SIZE_IN_BITS = 32;

    public IntegerEncoding {
        if (sizeInBits < 1 || sizeInBits > MAX_SIZE_IN_BITS) {
            throw new IllegalArgumentException("sizeInBits must be between 1 and " + MAX_SIZE_IN_BITS + ", was "
                    + sizeInBits);
        }
    }

    long decode(BitReader reader) {
        return reader.read(sizeInBits, signed);
    }
}
