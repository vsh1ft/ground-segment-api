package lt.vsh1ft.groundstation.satnogs;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("groundstation.satnogs.import")
record SatnogsImportProperties(List<Integer> satellites, Duration interval, Duration lookBack, Duration settle) {

    SatnogsImportProperties {
        satellites = satellites == null ? List.of() : List.copyOf(satellites);
        requirePositive("interval", interval);
        requirePositive("look-back", lookBack);
        if (settle == null || settle.isNegative()) {
            throw new IllegalArgumentException("groundstation.satnogs.import.settle must be zero or more, was "
                    + settle);
        }
    }

    private static void requirePositive(String name, Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("groundstation.satnogs.import." + name + " must be positive, was "
                    + duration);
        }
    }
}
