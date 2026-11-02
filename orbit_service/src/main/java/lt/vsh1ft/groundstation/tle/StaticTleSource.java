package lt.vsh1ft.groundstation.tle;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

import lt.vsh1ft.groundstation.pass.NoradId;
import org.orekit.propagation.analytical.tle.TLE;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOn("orekitDataLoader")
public class StaticTleSource implements TleSource {

    private final Map<NoradId, TLE> tles;

    public StaticTleSource(TleProperties properties) {
        this.tles = properties.tles().stream()
                .map(l -> new TLE(l.line1(), l.line2()))
                .collect(Collectors.toUnmodifiableMap(
                        tle -> new NoradId(tle.getSatelliteNumber()),
                        Function.identity(),
                        StaticTleSource::newer));
    }

    @Override
    public TLE latest(NoradId satellite) {
        final var tle = tles.get(satellite);
        if (tle == null) {
            throw new NoSuchElementException("No TLE for NORAD ID " + satellite);
        }
        return tle;
    }

    private static TLE newer(TLE a, TLE b) {
        return a.getDate().isAfterOrEqualTo(b) ? a : b;
    }
}
