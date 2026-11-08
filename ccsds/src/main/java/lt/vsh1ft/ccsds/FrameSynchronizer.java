package lt.vsh1ft.ccsds;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;

public final class FrameSynchronizer {

    public static final int ATTACHED_SYNC_MARKER = 0x1ACFFC1D;
    public static final int MARKER_LENGTH = 4;

    private final InputStream input;
    private final int frameLength;
    private boolean locked;
    private long skippedBytes;

    public FrameSynchronizer(InputStream input, int frameLength) {
        this.input = Objects.requireNonNull(input, "input");
        Fields.requireRange("frame length", frameLength, TmFrame.length(1), Integer.MAX_VALUE);
        this.frameLength = frameLength;
    }

    public static byte[] cadu(byte[] frame) {
        return ByteBuffer.allocate(MARKER_LENGTH + frame.length)
                .putInt(ATTACHED_SYNC_MARKER)
                .put(Randomizer.apply(frame))
                .array();
    }

    public Optional<byte[]> next() throws IOException {
        if (!(locked ? markerFollows() : searchMarker(0, 0))) {
            return Optional.empty();
        }
        final var frame = input.readNBytes(frameLength);
        if (frame.length < frameLength) {
            return Optional.empty();
        }
        locked = true;
        return Optional.of(Randomizer.apply(frame));
    }

    public long skippedBytes() {
        return skippedBytes;
    }

    private boolean markerFollows() throws IOException {
        final var marker = input.readNBytes(MARKER_LENGTH);
        if (marker.length < MARKER_LENGTH) {
            return false;
        }
        final var window = ByteBuffer.wrap(marker).getInt();
        if (window == ATTACHED_SYNC_MARKER) {
            return true;
        }
        locked = false;
        return searchMarker(window, MARKER_LENGTH);
    }

    private boolean searchMarker(int start, int startLength) throws IOException {
        var window = start;
        var length = startLength;
        while (length < MARKER_LENGTH || window != ATTACHED_SYNC_MARKER) {
            final var next = input.read();
            if (next < 0) {
                return false;
            }
            if (length >= MARKER_LENGTH) {
                skippedBytes++;
            }
            window = window << Byte.SIZE | next;
            length++;
        }
        return true;
    }
}
