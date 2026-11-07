package lt.vsh1ft.groundstation.radio;

import java.time.Instant;
import java.util.Locale;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SatnogsTransmitter(
        String uuid,
        Integer noradCatId,
        String description,
        String type,
        String status,
        Long uplinkLow,
        Long uplinkHigh,
        Long downlinkLow,
        Long downlinkHigh,
        String mode,
        String uplinkMode,
        Double baud,
        boolean invert,
        Instant updated) {

    Transmitter toTransmitter(Instant syncedAt) {
        return new Transmitter(uuid, noradCatId, description, parse(TransmitterType.class, type),
                parse(TransmitterStatus.class, status), uplinkLow, uplinkHigh, downlinkLow, downlinkHigh, mode,
                uplinkMode, baud, invert, updated, syncedAt);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }
}
