package lt.vsh1ft.ccsds;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;

public final class TmPacketReader {

    private final InputStream input;

    public TmPacketReader(InputStream input) {
        this.input = Objects.requireNonNull(input, "input");
    }

    public Optional<TmPacket> next() throws IOException {
        final var header = input.readNBytes(PrimaryHeader.LENGTH);
        if (header.length == 0) {
            return Optional.empty();
        }
        requireComplete(header, PrimaryHeader.LENGTH);
        final var dataFieldLength = PrimaryHeader.readFrom(ByteBuffer.wrap(header)).dataFieldLength();
        final var dataField = input.readNBytes(dataFieldLength);
        requireComplete(dataField, dataFieldLength);
        return Optional.of(TmPacket.decode(ByteBuffer.allocate(header.length + dataField.length)
                .put(header)
                .put(dataField)
                .array()));
    }

    private static void requireComplete(byte[] bytes, int expectedLength) throws EOFException {
        if (bytes.length < expectedLength) {
            throw new EOFException("the stream ended inside a packet: expected " + expectedLength
                    + " bytes, got " + bytes.length);
        }
    }
}
