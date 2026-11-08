package lt.vsh1ft.groundstation.contact;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("groundstation.contact")
record ContactProperties(Duration silenceThreshold, Duration planInterval) {

    ContactProperties {
        requirePositive("silence-threshold", silenceThreshold);
        requirePositive("plan-interval", planInterval);
    }

    private static void requirePositive(String name, Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("groundstation.contact." + name + " must be positive, was " + duration);
        }
    }
}
