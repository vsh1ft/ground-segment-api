package lt.vsh1ft.groundstation.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import lt.vsh1ft.groundstation.pass.NoradId;
import lt.vsh1ft.groundstation.pass.Pass;
import lt.vsh1ft.groundstation.station.Station;

public record PassesResponse(int satellite, UUID station, List<PassView> passes) {

    static PassesResponse of(NoradId satellite, Station station, List<Pass> passes) {
        return new PassesResponse(satellite.value(), station.id(), passes.stream().map(PassView::of).toList());
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
