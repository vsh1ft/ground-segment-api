package lt.vsh1ft.groundstation.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import lt.vsh1ft.groundstation.pass.GroundStation;
import lt.vsh1ft.groundstation.pass.NoradId;
import lt.vsh1ft.groundstation.pass.Pass;

public record PassesResponse(int satellite, String station, List<PassView> passes) {

    static PassesResponse of(NoradId satellite, GroundStation station, List<Pass> passes) {
        return new PassesResponse(satellite.value(), station.name(), passes.stream().map(PassView::of).toList());
    }

    public record PassView(Instant start, Peak peak, Instant end) {

        static PassView of(Pass pass) {
            return new PassView(
                    truncatedToMillis(pass.start()),
                    new Peak(truncatedToMillis(pass.peak().time()), roundedToHundredths(pass.peak().elevationDeg())),
                    truncatedToMillis(pass.end()));
        }
    }

    public record Peak(Instant time, double elevationDeg) {
    }

    private static Instant truncatedToMillis(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MILLIS);
    }

    private static double roundedToHundredths(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
