package lt.vsh1ft.groundstation.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import lt.vsh1ft.groundstation.catalog.SatelliteService;
import lt.vsh1ft.groundstation.elements.TleSource;
import lt.vsh1ft.groundstation.station.GroundStation;
import lt.vsh1ft.groundstation.station.GroundStationSource;
import lt.vsh1ft.groundstation.radio.Transmitter;
import lt.vsh1ft.groundstation.radio.TransmitterService;
import lt.vsh1ft.groundstation.radio.TransmitterStatus;
import lt.vsh1ft.groundstation.radio.TransmitterType;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.time.AbsoluteDate;

class TrackServiceTest {

    private static final NoradId ISS = new NoradId(25544);
    private static final UUID STATION_ID = UUID.fromString("3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42");
    private static final GroundStation VILNIUS = new GroundStation("Vilnius", 54.67519, 25.23422, 114.0, 5.0);
    private static final Instant NOW = Instant.parse("2026-11-01T00:41:17Z");
    private static final Instant EPOCH = Instant.parse("2026-10-31T04:14:04Z");
    private static final double APPROACHING = -6.0;

    private final SatelliteService satellites = mock(SatelliteService.class);
    private final GroundStationSource stations = mock(GroundStationSource.class);
    private final TleSource tles = mock(TleSource.class);
    private final TransmitterService transmitters = mock(TransmitterService.class);
    private final OrekitTracker tracker = mock(OrekitTracker.class);
    private final TLE tle = mock(TLE.class);
    private final TrackService service = new TrackService(satellites, stations, tles, transmitters, tracker,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void issOverVilnius() {
        given(stations.byId(STATION_ID)).willReturn(VILNIUS);
        given(tles.latest(ISS)).willReturn(tle);
        given(tle.getDate()).willReturn(new AbsoluteDate(EPOCH));
    }

    @Test
    @Requirement({"TRK-010", "TRK-020"})
    void trackStartsNowAndCorrectsEachFrequencyOfEveryActiveTransmitterOnce() {
        given(transmitters.transmitters(ISS, TransmitterStatus.ACTIVE)).willReturn(List.of(
                transmitter("aprs", TransmitterType.TRANSCEIVER, 145825000L, 145825000L, 145825000L, 145825000L),
                transmitter("beacon", TransmitterType.TRANSMITTER, 437800000L, 437800000L, null, null),
                transmitter("linear", TransmitterType.TRANSPONDER, 435765000L, 435795000L, 145935000L, 145965000L),
                transmitter("narrow", TransmitterType.TRANSPONDER, 435800000L, 435800000L, 145900000L, null)));
        given(tracker.track(tle, VILNIUS, NOW, Duration.ofSeconds(1), 901)).willReturn(List.of(geometry(NOW)));

        final var track = service.track(ISS, query(null, null, null, null));

        assertThat(track.satellite()).isEqualTo(25544);
        assertThat(track.station()).isEqualTo(STATION_ID);
        assertThat(track.elementsEpoch()).isEqualTo(EPOCH);
        assertThat(track.step()).isEqualTo(Duration.ofSeconds(1));
        assertThat(track.frequencies()).extracting(Frequency::transmitter, Frequency::direction, Frequency::nominalHz)
                .containsExactly(
                        tuple("aprs", Direction.DOWNLINK, 145825000L),
                        tuple("aprs", Direction.UPLINK, 145825000L),
                        tuple("beacon", Direction.DOWNLINK, 437800000L),
                        tuple("linear", Direction.DOWNLINK, 435765000L),
                        tuple("linear", Direction.DOWNLINK, 435795000L),
                        tuple("linear", Direction.UPLINK, 145935000L),
                        tuple("linear", Direction.UPLINK, 145965000L),
                        tuple("narrow", Direction.DOWNLINK, 435800000L),
                        tuple("narrow", Direction.UPLINK, 145900000L));
        final var point = track.points().getFirst();
        assertThat(point.rangeRateKmPerS()).isEqualTo(APPROACHING);
        assertThat(point.frequenciesHz()).hasSize(9).first().isEqualTo(145827919L);
        assertThat(point.frequenciesHz().get(1)).isEqualTo(145822082L);
    }

    @Test
    @Requirement("TRK-020")
    void requestedFrequenciesReplaceTheTransmitters() {
        final var from = Instant.parse("2026-11-01T00:45:00Z");
        given(tracker.track(tle, VILNIUS, from, Duration.ofSeconds(2), 31)).willReturn(List.of(geometry(from)));

        final var track = service.track(ISS, new TrackQuery(STATION_ID, from, Duration.ofMinutes(1),
                Duration.ofSeconds(2), 437000000L, 145900000L));

        assertThat(track.frequencies()).containsExactly(
                new Frequency(null, "requested", Direction.DOWNLINK, 437000000L),
                new Frequency(null, "requested", Direction.UPLINK, 145900000L));
        verifyNoInteractions(transmitters);
    }

    @Test
    @Requirement("TRK-020")
    void onlyTheRequestedDirectionIsCorrected() {
        given(tracker.track(tle, VILNIUS, NOW, Duration.ofSeconds(1), 901)).willReturn(List.of(geometry(NOW)));

        assertThat(service.track(ISS, query(null, null, null, 145900000L)).frequencies()).singleElement()
                .extracting(Frequency::direction).isEqualTo(Direction.UPLINK);
        assertThat(service.track(ISS, query(null, null, 437000000L, null)).frequencies()).singleElement()
                .extracting(Frequency::direction).isEqualTo(Direction.DOWNLINK);
    }

    @Test
    @Requirement("TRK-020")
    void satelliteWithoutActiveTransmittersHasATrackWithoutFrequencies() {
        given(transmitters.transmitters(ISS, TransmitterStatus.ACTIVE)).willReturn(List.of());
        given(tracker.track(tle, VILNIUS, NOW, Duration.ofSeconds(1), 901)).willReturn(List.of(geometry(NOW)));

        final var track = service.track(ISS, query(null, null, null, null));

        assertThat(track.frequencies()).isEmpty();
        assertThat(track.points().getFirst().frequenciesHz()).isEmpty();
    }

    @Test
    @Requirement("TRK-040")
    void tenThousandPointsAreAllowedAndOneMoreIsNot() {
        given(transmitters.transmitters(ISS, TransmitterStatus.ACTIVE)).willReturn(List.of());
        given(tracker.track(tle, VILNIUS, NOW, Duration.ofSeconds(1), 10_000)).willReturn(List.of());

        assertThat(service.track(ISS, query(Duration.ofSeconds(9_999), null, null, null)).points()).isEmpty();
        assertThatThrownBy(() -> service.track(ISS, query(Duration.ofSeconds(10_000), null, null, null)))
                .isInstanceOf(TooManyPointsException.class)
                .hasMessage("A track of PT2H46M40S at a step of PT1S has 10001 points; at most 10000 are allowed, "
                        + "so ask for a shorter track or a longer step");
    }

    private static TrackQuery query(Duration duration, Duration step, Long downlinkHz, Long uplinkHz) {
        return new TrackQuery(STATION_ID, null, duration, step, downlinkHz, uplinkHz);
    }

    private static OrekitTracker.Geometry geometry(Instant time) {
        return new OrekitTracker.Geometry(time, 262.0, 5.0, 1876.8, APPROACHING);
    }

    private static Transmitter transmitter(String uuid, TransmitterType type, Long downlinkLow, Long downlinkHigh,
                                           Long uplinkLow, Long uplinkHigh) {
        return new Transmitter(uuid, ISS.value(), uuid, type, TransmitterStatus.ACTIVE, uplinkLow, uplinkHigh,
                downlinkLow, downlinkHigh, "FM", "FM", null, false, NOW, NOW);
    }
}
