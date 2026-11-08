package lt.vsh1ft.ccsds;

public final class Randomizer {

    private static final int PERIOD = 255;
    private static final byte[] SEQUENCE = sequence();

    private Randomizer() {
    }

    public static byte[] apply(byte[] data) {
        final var result = new byte[data.length];
        for (var i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ SEQUENCE[i % PERIOD]);
        }
        return result;
    }

    private static byte[] sequence() {
        final var bytes = new byte[PERIOD];
        var state = 0xFF;
        for (var bit = 0; bit < PERIOD * Byte.SIZE; bit++) {
            final var out = state >>> 7 & 1;
            final var feedback = (state >>> 7 ^ state >>> 4 ^ state >>> 2 ^ state) & 1;
            state = (state << 1 | feedback) & 0xFF;
            bytes[bit / Byte.SIZE] |= (byte) (out << (7 - bit % Byte.SIZE));
        }
        return bytes;
    }
}
