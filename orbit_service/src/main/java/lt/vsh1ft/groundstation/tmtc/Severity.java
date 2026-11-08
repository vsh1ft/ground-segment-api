package lt.vsh1ft.groundstation.tmtc;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.swagger.v3.oas.annotations.media.Schema;
import lt.vsh1ft.ccsds.MessageType;

@Schema(description = "Event severity, from the PUS subtype: INFO = TM[5,1], LOW = TM[5,2], MEDIUM = TM[5,3], "
        + "HIGH = TM[5,4]")
public enum Severity {
    INFO, LOW, MEDIUM, HIGH;

    private static final int EVENT_REPORTING = 5;

    static Optional<Severity> of(MessageType type) {
        final int subtype = type.messageSubtype();
        if (type.serviceType() != EVENT_REPORTING || subtype < 1 || subtype > values().length) {
            return Optional.empty();
        }
        return Optional.of(values()[subtype - 1]);
    }

    List<Severity> andWorse() {
        return Arrays.stream(values()).filter(severity -> severity.compareTo(this) >= 0).toList();
    }
}
