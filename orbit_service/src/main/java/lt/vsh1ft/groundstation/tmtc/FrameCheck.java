package lt.vsh1ft.groundstation.tmtc;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32C;

enum FrameCheck {

    NONE,
    CRC32C_TRAILER;

    private static final int TRAILER_LENGTH = Integer.BYTES;

    Checked check(byte[] frame) {
        if (this == NONE) {
            return new Checked(frame, null);
        }
        if (frame.length > TRAILER_LENGTH && trailerMatches(frame)) {
            return new Checked(Arrays.copyOf(frame, frame.length - TRAILER_LENGTH), true);
        }
        return new Checked(frame, false);
    }

    private static boolean trailerMatches(byte[] frame) {
        final var crc = new CRC32C();
        crc.update(frame, 0, frame.length - TRAILER_LENGTH);
        return (int) crc.getValue() == ByteBuffer.wrap(frame, frame.length - TRAILER_LENGTH, TRAILER_LENGTH).getInt();
    }

    record Checked(byte[] content, Boolean crcChecked) {
    }
}
