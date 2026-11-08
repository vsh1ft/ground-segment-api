package lt.vsh1ft.simulator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;

import org.hipparchus.util.FastMath;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.TopocentricFrame;
import org.orekit.models.earth.ReferenceEllipsoid;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.IERSConventions;

final class StationView {

    private final TLE tle;
    private final TLEPropagator propagator;
    private final TopocentricFrame station;
    private final double minElevationRad;

    StationView(TLE tle, GroundContact contact) {
        this.tle = tle;
        this.propagator = TLEPropagator.selectExtrapolator(tle);
        final var earth = ReferenceEllipsoid.getWgs84(FramesFactory.getITRF(IERSConventions.IERS_2010, true));
        this.station = new TopocentricFrame(earth, new GeodeticPoint(FastMath.toRadians(contact.latitudeDeg()),
                FastMath.toRadians(contact.longitudeDeg()), contact.altitudeM()), "station");
        this.minElevationRad = FastMath.toRadians(contact.minElevationDeg());
    }

    static StationView load(GroundContact contact) {
        DataContext.getDefault().getDataProvidersManager()
                .addProvider(new DirectoryCrawler(contact.orekitData().toFile()));
        try {
            final var lines = Files.readAllLines(contact.tleFile()).stream().map(String::strip)
                    .filter(line -> !line.isEmpty()).toList();
            if (lines.size() < 2) {
                throw new IllegalArgumentException(contact.tleFile() + " holds no TLE");
            }
            return new StationView(new TLE(lines.get(lines.size() - 2), lines.getLast()), contact);
        } catch (IOException e) {
            throw new UncheckedIOException("Can't read " + contact.tleFile(), e);
        }
    }

    boolean inView(Instant at) {
        final var date = new AbsoluteDate(at);
        final var position = propagator.propagate(date).getPosition();
        return station.getElevation(position, propagator.getFrame(), date) >= minElevationRad;
    }

    Duration orbitPeriod() {
        return Duration.ofNanos(Math.round(2 * Math.PI / tle.getMeanMotion() * 1e9));
    }
}
