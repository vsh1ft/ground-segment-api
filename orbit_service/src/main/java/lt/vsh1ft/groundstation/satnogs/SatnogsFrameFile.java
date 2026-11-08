package lt.vsh1ft.groundstation.satnogs;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

final class SatnogsFrameFile {

    private static final Pattern NAME = Pattern.compile("data_\\d+_(\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2})(_\\d+)?$");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");
    private static final Pattern HEX = Pattern.compile("(?:[0-9A-Fa-f]{2})+");

    private SatnogsFrameFile() {
    }

    static Optional<Instant> receivedAt(String url) {
        final var matcher = NAME.matcher(url);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(LocalDateTime.parse(matcher.group(1), TIME).toInstant(ZoneOffset.UTC));
    }

    static byte[] frame(byte[] content) {
        final var text = new String(content, StandardCharsets.US_ASCII).strip();
        return HEX.matcher(text).matches() ? HexFormat.of().parseHex(text) : content;
    }
}
