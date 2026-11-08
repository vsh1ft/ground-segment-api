package lt.vsh1ft.groundstation.pass;

import java.time.Instant;
import java.util.Objects;

public record Pass(Crossing start, Crossing end, Peak peak) {

    public Pass {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(peak, "peak");
        if (!start.time().isBefore(end.time())) {
            throw new IllegalArgumentException("Pass must start before it ends: " + start.time() + " / "
                    + end.time());
        }
        if (peak.time().isBefore(start.time()) || peak.time().isAfter(end.time())) {
            throw new IllegalArgumentException("Peak " + peak.time() + " is outside the pass " + start.time() + " / "
                    + end.time());
        }
    }

    public record Crossing(Instant time, double azimuthDeg) {

        public Crossing {
            Objects.requireNonNull(time, "time");
            requireAzimuth(azimuthDeg);
        }
    }

    public record Peak(Instant time, double elevationDeg, double azimuthDeg) {

        public Peak {
            Objects.requireNonNull(time, "time");
            if (elevationDeg < -90 || elevationDeg > 90) {
                throw new IllegalArgumentException("Elevation out of range: " + elevationDeg);
            }
            requireAzimuth(azimuthDeg);
        }
    }

    private static void requireAzimuth(double azimuthDeg) {
        if (azimuthDeg < 0 || azimuthDeg >= 360) {
            throw new IllegalArgumentException("Azimuth must be at least 0 and below 360 degrees: " + azimuthDeg);
        }
    }
}
