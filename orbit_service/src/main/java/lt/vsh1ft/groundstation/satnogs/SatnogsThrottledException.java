package lt.vsh1ft.groundstation.satnogs;

import java.time.Duration;
import java.util.Optional;

class SatnogsThrottledException extends RuntimeException {

    private final transient Duration retryAfter;

    SatnogsThrottledException(Duration retryAfter, Throwable cause) {
        super("SatNOGS asked to slow down", cause);
        this.retryAfter = retryAfter;
    }

    Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
