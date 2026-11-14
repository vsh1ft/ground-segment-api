package lt.vsh1ft.groundstation.tracking;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lt.vsh1ft.groundstation.station.GroundStation;
import org.hipparchus.util.FastMath;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.TopocentricFrame;
import org.orekit.models.earth.ReferenceEllipsoid;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.IERSConventions;
import org.springframework.stereotype.Component;

@Component
class OrekitTracker {

    private static final double FULL_CIRCLE_DEG = 360.0;
    private static final double METRES_PER_KM = 1000.0;

    List<Geometry> track(TLE tle, GroundStation station, Instant from, Duration step, long points) {
        final var propagator = TLEPropagator.selectExtrapolator(tle);
        final var frame = stationFrame(station);
        final var track = new ArrayList<Geometry>();
        for (var i = 0L; i < points; i++) {
            final var time = from.plus(step.multipliedBy(i));
            final var date = new AbsoluteDate(time);
            final var state = propagator.propagate(date);
            final var coordinates = frame.getTrackingCoordinates(state.getPosition(), state.getFrame(), date);
            final var rangeRate = frame.getRangeRate(state.getPVCoordinates(), state.getFrame(), date);
            track.add(new Geometry(time, FastMath.toDegrees(coordinates.getAzimuth()) % FULL_CIRCLE_DEG,
                    FastMath.toDegrees(coordinates.getElevation()), coordinates.getRange() / METRES_PER_KM,
                    rangeRate / METRES_PER_KM));
        }
        return track;
    }

    private static TopocentricFrame stationFrame(GroundStation station) {
        final var earth = ReferenceEllipsoid.getWgs84(FramesFactory.getITRF(IERSConventions.IERS_2010, true));
        final var point = new GeodeticPoint(FastMath.toRadians(station.latitudeDeg()),
                FastMath.toRadians(station.longitudeDeg()), station.altitudeM());
        return new TopocentricFrame(earth, point, station.name());
    }

    record Geometry(Instant time, double azimuthDeg, double elevationDeg, double rangeKm, double rangeRateKmPerS) {
    }
}
