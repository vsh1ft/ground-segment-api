package lt.vsh1ft.groundstation.pass.orekit;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.ObjDoubleConsumer;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.elements.TleSource;
import lt.vsh1ft.groundstation.pass.GroundStation;
import lt.vsh1ft.groundstation.pass.PassAssembler;
import lt.vsh1ft.groundstation.pass.PassForecast;
import lt.vsh1ft.groundstation.pass.PassPredictor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.TopocentricFrame;
import org.orekit.models.earth.ReferenceEllipsoid;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.propagation.events.ElevationDetector;
import org.orekit.propagation.events.ElevationExtremumDetector;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.events.EventSlopeFilter;
import org.orekit.propagation.events.FilterType;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.TrackingCoordinates;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrekitPassPredictor implements PassPredictor {

    private static final double FULL_CIRCLE_DEG = 360.0;

    private final TleSource tleSource;
    private final PassDetectionProperties detection;

    @Override
    public PassForecast predict(NoradId satellite, GroundStation station, Instant from, Duration duration) {
        final var tle = tleSource.latest(satellite);
        final var propagator = TLEPropagator.selectExtrapolator(tle);
        final var stationFrame = toFrame(station);
        final var visibility = visibilityDetector(stationFrame, station.minElevationDeg());
        final var assembler = new PassAssembler();

        propagator.addEventDetector(acquisitionDetector(visibility, stationFrame, assembler::onAcquisition));
        propagator.addEventDetector(lossDetector(visibility, stationFrame, assembler::onLoss));
        propagator.addEventDetector(maximumDetector(stationFrame, assembler::onMaximum));

        final var start = new AbsoluteDate(from);
        propagator.propagate(start, start.shiftedBy(duration.toNanos(), TimeUnit.NANOSECONDS));

        return new PassForecast(tle.getDate().toInstant(), assembler.passes());
    }

    private static EventDetector acquisitionDetector(ElevationDetector visibility, TopocentricFrame stationFrame,
                                                     ObjDoubleConsumer<Instant> onAcquisition) {
        return new EventSlopeFilter<>(
                visibility.withHandler((state, _, _) -> {
                    onAcquisition.accept(state.getDate().toInstant(), azimuthDeg(tracking(stationFrame, state)));
                    return Action.CONTINUE;
                }),
                FilterType.TRIGGER_ONLY_INCREASING_EVENTS);
    }

    private static EventDetector lossDetector(ElevationDetector visibility, TopocentricFrame stationFrame,
                                              ObjDoubleConsumer<Instant> onLoss) {
        return new EventSlopeFilter<>(
                visibility.withHandler((state, _, _) -> {
                    onLoss.accept(state.getDate().toInstant(), azimuthDeg(tracking(stationFrame, state)));
                    return Action.CONTINUE;
                }),
                FilterType.TRIGGER_ONLY_DECREASING_EVENTS);
    }

    private ElevationDetector visibilityDetector(TopocentricFrame stationFrame, double minElevationDeg) {
        return new ElevationDetector(detection.maxCheckS(), detection.thresholdS(), stationFrame)
                .withConstantElevation(FastMath.toRadians(minElevationDeg));
    }

    private EventDetector maximumDetector(TopocentricFrame stationFrame, MaximumHandler onMaximum) {
        final var extremum = new ElevationExtremumDetector(stationFrame)
                .withMaxCheck(detection.maxCheckS())
                .withThreshold(detection.thresholdS())
                .withHandler((state, _, _) -> {
                    final var tracking = tracking(stationFrame, state);
                    onMaximum.accept(state.getDate().toInstant(), FastMath.toDegrees(tracking.getElevation()),
                            azimuthDeg(tracking));
                    return Action.CONTINUE;
                });
        return new EventSlopeFilter<>(extremum, FilterType.TRIGGER_ONLY_DECREASING_EVENTS);
    }

    private static TopocentricFrame toFrame(GroundStation station) {
        final var earth = ReferenceEllipsoid.getWgs84(FramesFactory.getITRF(IERSConventions.IERS_2010, true));
        final var point = new GeodeticPoint(
                FastMath.toRadians(station.latitudeDeg()),
                FastMath.toRadians(station.longitudeDeg()),
                station.altitudeM());
        return new TopocentricFrame(earth, point, station.name());
    }

    private static TrackingCoordinates tracking(TopocentricFrame stationFrame, SpacecraftState state) {
        return stationFrame.getTrackingCoordinates(state.getPosition(), state.getFrame(), state.getDate());
    }

    private static double azimuthDeg(TrackingCoordinates tracking) {
        return FastMath.toDegrees(tracking.getAzimuth()) % FULL_CIRCLE_DEG;
    }

    @FunctionalInterface
    private interface MaximumHandler {

        void accept(Instant time, double elevationDeg, double azimuthDeg);
    }
}
