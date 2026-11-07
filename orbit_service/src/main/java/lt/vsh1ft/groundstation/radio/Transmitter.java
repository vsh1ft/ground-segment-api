package lt.vsh1ft.groundstation.radio;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("transmitter")
public record Transmitter(
        @Id String uuid,
        int noradId,
        String description,
        TransmitterType type,
        TransmitterStatus status,
        Long uplinkLowHz,
        Long uplinkHighHz,
        Long downlinkLowHz,
        Long downlinkHighHz,
        String mode,
        String uplinkMode,
        Double baud,
        boolean invert,
        Instant updatedAt,
        Instant syncedAt) {
}
