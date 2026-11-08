package lt.vsh1ft.groundstation.contact;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("groundstation.contact")
record ContactProperties(Duration silenceThreshold, Duration planInterval, double maxFrameLoss) {

    ContactProperties {
        requirePositive("silence-threshold", silenceThreshold);
        requirePositive("plan-interval", planInterval);
        if (maxFrameLoss < 0 || maxFrameLoss >= 1) {
            throw new IllegalArgumentException("groundstation.contact.max-frame-loss must be at least 0 and below 1, "
                    + "was " + maxFrameLoss);
        }
    }

    private static void requirePositive(String name, Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("groundstation.contact." + name + " must be positive, was " + duration);
        }
    }
}
