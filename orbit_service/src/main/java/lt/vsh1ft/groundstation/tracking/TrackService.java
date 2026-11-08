package lt.vsh1ft.groundstation.tracking;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.catalog.SatelliteService;
import lt.vsh1ft.groundstation.elements.TleSource;
import lt.vsh1ft.groundstation.pass.GroundStationSource;
import lt.vsh1ft.groundstation.radio.TransmitterResponse;
import lt.vsh1ft.groundstation.radio.TransmitterService;
import lt.vsh1ft.groundstation.radio.TransmitterStatus;
import lt.vsh1ft.groundstation.radio.TransmitterType;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class TrackService {

    static final long MAX_POINTS = 10_000;
    private static final String REQUESTED = "requested";

    private final SatelliteService satellites;
    private final GroundStationSource stations;
    private final TleSource tles;
    private final TransmitterService transmitters;
    private final OrekitTracker tracker;
    private final Clock clock;

    TrackResponse track(NoradId satellite, TrackQuery query) {
        if (query.points() > MAX_POINTS) {
            throw new TooManyPointsException(query.duration(), query.step(), query.points(), MAX_POINTS);
        }
        satellites.ensureInCatalog(satellite);
        final var station = stations.byId(query.station());
        final var frequencies = query.requestsFrequencies() ? requested(query) : ofTransmitters(satellite);
        final var tle = tles.latest(satellite);
        final var from = Objects.requireNonNullElseGet(query.from(), clock::instant);
        final var points = tracker.track(tle, station, from, query.step(), query.points()).stream()
                .map(geometry -> new TrackPoint(geometry.time(), geometry.azimuthDeg(), geometry.elevationDeg(),
                        geometry.rangeKm(), geometry.rangeRateKmPerS(), frequencies.stream()
                        .map(frequency -> frequency.correctedHz(geometry.rangeRateKmPerS())).toList()))
                .toList();
        return new TrackResponse(satellite.value(), query.station(), tle.getDate().toInstant(), query.step(),
                frequencies, points);
    }

    private static List<Frequency> requested(TrackQuery query) {
        final var frequencies = new ArrayList<Frequency>();
        if (query.downlinkHz() != null) {
            frequencies.add(new Frequency(null, REQUESTED, Direction.DOWNLINK, query.downlinkHz()));
        }
        if (query.uplinkHz() != null) {
            frequencies.add(new Frequency(null, REQUESTED, Direction.UPLINK, query.uplinkHz()));
        }
        return frequencies;
    }

    private List<Frequency> ofTransmitters(NoradId satellite) {
        return transmitters.transmitters(satellite, TransmitterStatus.ACTIVE).stream()
                .flatMap(TrackService::frequencies)
                .toList();
    }

    private static Stream<Frequency> frequencies(TransmitterResponse transmitter) {
        final var band = transmitter.type() == TransmitterType.TRANSPONDER;
        return Stream.of(
                        frequency(transmitter, Direction.DOWNLINK, transmitter.downlinkLowHz()),
                        band ? frequency(transmitter, Direction.DOWNLINK, transmitter.downlinkHighHz()) : null,
                        frequency(transmitter, Direction.UPLINK, transmitter.uplinkLowHz()),
                        band ? frequency(transmitter, Direction.UPLINK, transmitter.uplinkHighHz()) : null)
                .filter(Objects::nonNull)
                .distinct();
    }

    private static Frequency frequency(TransmitterResponse transmitter, Direction direction, Long hz) {
        return hz == null ? null : new Frequency(transmitter.uuid(), transmitter.description(), direction, hz);
    }
}
