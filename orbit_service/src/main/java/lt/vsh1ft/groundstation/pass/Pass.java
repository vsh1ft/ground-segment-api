package lt.vsh1ft.groundstation.pass;

import java.time.Instant;
import java.util.Objects;

public record Pass(Instant start, Instant end, Peak peak) {

    public Pass {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(peak, "peak");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Pass must start before it ends: " + start + " / " + end);
        }
        if (peak.time().isBefore(start) || peak.time().isAfter(end)) {
            throw new IllegalArgumentException("Peak " + peak.time() + " is outside the pass " + start + " / " + end);
        }
    }

    public record Peak(Instant time, double elevationDeg) {

        public Peak {
            Objects.requireNonNull(time, "time");
            if (elevationDeg < -90 || elevationDeg > 90) {
                throw new IllegalArgumentException("Elevation out of range: " + elevationDeg);
            }
        }
    }
}
