package lt.vsh1ft.groundstation.orekit;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.ObjDoubleConsumer;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.pass.GroundStation;
import lt.vsh1ft.groundstation.pass.NoradId;
import lt.vsh1ft.groundstation.pass.Pass;
import lt.vsh1ft.groundstation.pass.PassAssembler;
import lt.vsh1ft.groundstation.pass.PassPredictor;
import lt.vsh1ft.groundstation.tle.TleSource;
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
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrekitPassPredictor implements PassPredictor {

    private final TleSource tleSource;
    private final PassDetectionProperties detection;

    @Override
    public List<Pass> predict(NoradId satellite, GroundStation station, Instant from, Duration duration) {
        final var propagator = TLEPropagator.selectExtrapolator(tleSource.latest(satellite));
        final var stationFrame = toFrame(station);
        final var assembler = new PassAssembler();

        propagator.addEventDetector(acquisitionDetector(stationFrame, assembler::onAcquisition));
        propagator.addEventDetector(lossDetector(stationFrame, assembler::onLoss));
        propagator.addEventDetector(maximumDetector(stationFrame, assembler::onMaximum));

        final var start = new AbsoluteDate(from);
        propagator.propagate(start, start.shiftedBy(duration.toNanos(), TimeUnit.NANOSECONDS));

        return assembler.passes();
    }

    private EventDetector acquisitionDetector(TopocentricFrame stationFrame, Consumer<Instant> onAcquisition) {
        return new EventSlopeFilter<>(
                visibilityDetector(stationFrame).withHandler((state, _, _) -> {
                    onAcquisition.accept(state.getDate().toInstant());
                    return Action.CONTINUE;
                }),
                FilterType.TRIGGER_ONLY_INCREASING_EVENTS);
    }

    private EventDetector lossDetector(TopocentricFrame stationFrame, Consumer<Instant> onLoss) {
        return new EventSlopeFilter<>(
                visibilityDetector(stationFrame).withHandler((state, _, _) -> {
                    onLoss.accept(state.getDate().toInstant());
                    return Action.CONTINUE;
                }),
                FilterType.TRIGGER_ONLY_DECREASING_EVENTS);
    }

    private ElevationDetector visibilityDetector(TopocentricFrame stationFrame) {
        return new ElevationDetector(detection.maxCheckS(), detection.thresholdS(), stationFrame)
                .withConstantElevation(FastMath.toRadians(detection.minElevationDeg()));
    }

    private EventDetector maximumDetector(TopocentricFrame stationFrame, ObjDoubleConsumer<Instant> onMaximum) {
        final var extremum = new ElevationExtremumDetector(stationFrame)
                .withMaxCheck(detection.maxCheckS())
                .withThreshold(detection.thresholdS())
                .withHandler((state, _, _) -> {
                    onMaximum.accept(state.getDate().toInstant(), elevationDeg(stationFrame, state));
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

    private static double elevationDeg(TopocentricFrame stationFrame, SpacecraftState state) {
        return FastMath.toDegrees(stationFrame.getElevation(state.getPosition(), state.getFrame(), state.getDate()));
    }
}
