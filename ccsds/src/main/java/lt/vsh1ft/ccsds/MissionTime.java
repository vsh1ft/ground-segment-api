package lt.vsh1ft.ccsds;

import java.time.Duration;
import java.time.Instant;

public final class MissionTime {

    public static final Instant EPOCH = Instant.parse("1958-01-01T00:00:00Z");
    public static final long TAI_MINUS_UTC_SECONDS = 37;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final int FINE_BITS = 16;

    private MissionTime() {
    }

    public static CucTime toCuc(Instant utc) {
        final var sinceEpoch = Duration.between(EPOCH, utc).plusSeconds(TAI_MINUS_UTC_SECONDS);
        final var fine = ((long) sinceEpoch.getNano() << FINE_BITS) / NANOS_PER_SECOND;
        return new CucTime(sinceEpoch.getSeconds(), (int) fine);
    }

    public static Instant toUtc(CucTime time) {
        final var nanos = (time.fine() * NANOS_PER_SECOND) >>> FINE_BITS;
        return EPOCH.plusSeconds(time.coarse() - TAI_MINUS_UTC_SECONDS).plusNanos(nanos);
    }
}
