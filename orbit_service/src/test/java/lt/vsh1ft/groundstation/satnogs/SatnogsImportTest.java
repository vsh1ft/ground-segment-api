package lt.vsh1ft.groundstation.satnogs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import lt.vsh1ft.groundstation.catalog.SatelliteNotInCatalogException;
import lt.vsh1ft.groundstation.catalog.SatelliteService;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.frame.FrameReception;
import lt.vsh1ft.groundstation.frame.Reception;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.ResourceAccessException;

class SatnogsImportTest {

    private static final NoradId FRONTIERSAT = new NoradId(69015);
    private static final Instant NOW = Instant.parse("2026-11-08T20:00:00Z");
    private static final Instant H17 = Instant.parse("2026-11-08T17:00:00Z");
    private static final Instant H18 = Instant.parse("2026-11-08T18:00:00Z");
    private static final Instant H19 = Instant.parse("2026-11-08T19:00:00Z");
    private static final byte[] FRAME = {1, 2, 3};
    private static final SatnogsNetworkClient.Page NOTHING = new SatnogsNetworkClient.Page(List.of(), null);

    private final SatnogsNetworkClient client = mock(SatnogsNetworkClient.class);
    private final SatnogsImportState state = mock(SatnogsImportState.class);
    private final FrameReception reception = mock(FrameReception.class);
    private final SatelliteService catalog = mock(SatelliteService.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final SettableClock clock = new SettableClock(NOW);
    private final SatnogsImport satnogsImport = satnogsImport(List.of(69015), Duration.ofHours(3));

    @Test
    @Requirement("SAT-010")
    void firstImportWalksTheLookBackOldestFirstHourByHourAndSavesEachHour() {
        given(state.importedUntil(FRONTIERSAT)).willReturn(Optional.empty());
        final var observation = observation(7, "2026-11-08T17:40:00Z");
        given(client.observations(FRONTIERSAT, H17.minus(SatnogsImport.OVERLAP), H18, null))
                .willReturn(new SatnogsNetworkClient.Page(List.of(observation), null));
        given(client.observations(FRONTIERSAT, H18.minus(SatnogsImport.OVERLAP), H19, null)).willReturn(NOTHING);
        frames(observation);

        satnogsImport.importAll();

        final var order = inOrder(client, state);
        order.verify(client).observations(FRONTIERSAT, H17.minus(SatnogsImport.OVERLAP), H18, null);
        order.verify(state).save(FRONTIERSAT, H18);
        order.verify(client).observations(FRONTIERSAT, H18.minus(SatnogsImport.OVERLAP), H19, null);
        order.verify(state).save(FRONTIERSAT, H19);
        verify(reception).receive(FRONTIERSAT, FRAME, Reception.satnogs("Station 7", 25.0, 55.0, 32.0, 7,
                Instant.parse("2026-11-08T17:31:12Z")));
    }

    @Test
    @Requirement("SAT-010")
    void laterImportContinuesFromTheSavedPositionAndFollowsEveryPage() {
        final var saved = Instant.parse("2026-11-08T18:15:00Z");
        given(state.importedUntil(FRONTIERSAT)).willReturn(Optional.of(saved));
        final var first = observation(8, "2026-11-08T18:50:00Z");
        final var second = observation(9, "2026-11-08T18:20:00Z");
        given(client.observations(FRONTIERSAT, saved.minus(SatnogsImport.OVERLAP), H19, null))
                .willReturn(new SatnogsNetworkClient.Page(List.of(first), "cursor"));
        given(client.observations(FRONTIERSAT, saved.minus(SatnogsImport.OVERLAP), H19, "cursor"))
                .willReturn(new SatnogsNetworkClient.Page(List.of(second), null));
        frames(first, second);

        satnogsImport.importAll();

        verify(client).download(url(first));
        verify(client).download(url(second));
        verify(state, times(1)).save(any(), any());
        verify(state).save(FRONTIERSAT, H19);
    }

    @Test
    @Requirement("SAT-010")
    void importUpToDateAsksNothing() {
        given(state.importedUntil(FRONTIERSAT)).willReturn(Optional.of(H19));

        satnogsImport.importAll();

        verifyNoInteractions(client);
        verify(state, never()).save(any(), any());
    }

    @Test
    @Requirement("SAT-010")
    void frameFileWithoutATimeIsDatedByItsObservationStart() {
        given(state.importedUntil(FRONTIERSAT)).willReturn(Optional.of(H18));
        final var observation = new SatnogsObservation(7, Instant.parse("2026-11-08T18:40:00Z"),
                Instant.parse("2026-11-08T18:50:00Z"), "A", null, null, null,
                List.of(new SatnogsObservation.Demoddata("https://x/frame.bin")));
        given(client.observations(any(), any(), any(), any()))
                .willReturn(new SatnogsNetworkClient.Page(List.of(observation), null));
        given(client.download("https://x/frame.bin")).willReturn(FRAME);
        given(reception.receive(any(), any(), any())).willReturn(new FrameReception.Outcome(1, false, true));

        satnogsImport.importAll();

        verify(reception).receive(FRONTIERSAT, FRAME, Reception.satnogs("A", null, null, null, 7,
                Instant.parse("2026-11-08T18:40:00Z")));
    }

    @Test
    @Requirement("SAT-010")
    void frameThatFailsToDownloadIsSkippedAndTheImportGoesOn() {
        given(state.importedUntil(FRONTIERSAT)).willReturn(Optional.of(H18));
        final var broken = observation(2, "2026-11-08T18:20:00Z");
        final var fine = observation(3, "2026-11-08T18:40:00Z");
        given(client.observations(any(), any(), any(), any()))
                .willReturn(new SatnogsNetworkClient.Page(List.of(broken, fine), null));
        frames(fine);
        given(client.download(url(broken))).willThrow(new ResourceAccessException("503 Service Unavailable"));

        satnogsImport.importAll();

        verify(client).download(url(fine));
        verify(state).save(FRONTIERSAT, H19);
    }

    @Test
    @Requirement("SAT-010")
    void unreachableSatnogsKeepsTheHoursAlreadyImported() {
        given(state.importedUntil(FRONTIERSAT)).willReturn(Optional.of(H17));
        given(client.observations(FRONTIERSAT, H17.minus(SatnogsImport.OVERLAP), H18, null)).willReturn(NOTHING);
        given(client.observations(FRONTIERSAT, H18.minus(SatnogsImport.OVERLAP), H19, null))
                .willThrow(new ResourceAccessException("connection refused"));

        satnogsImport.importAll();

        verify(state).save(FRONTIERSAT, H18);
        verify(state, never()).save(FRONTIERSAT, H19);
    }

    @Test
    @Requirement("SAT-010")
    void throttledImportKeepsItsHoursAndWaitsAsLongAsSatnogsAsks() {
        given(state.importedUntil(FRONTIERSAT)).willReturn(Optional.of(H17));
        given(client.observations(FRONTIERSAT, H17.minus(SatnogsImport.OVERLAP), H18, null)).willReturn(NOTHING);
        given(client.observations(FRONTIERSAT, H18.minus(SatnogsImport.OVERLAP), H19, null))
                .willThrow(new SatnogsThrottledException(Duration.ofSeconds(1048), null));

        satnogsImport.importAll();
        clock.now = NOW.plusSeconds(1047);
        satnogsImport.importAll();
        clock.now = NOW.plusSeconds(1048);
        satnogsImport.importAll();

        verify(state, times(2)).save(FRONTIERSAT, H18);
        verify(client, times(2)).observations(FRONTIERSAT, H18.minus(SatnogsImport.OVERLAP), H19, null);
    }

    @Test
    @Requirement("SAT-010")
    void throttledImportWithoutAUsableWaitTimeWaitsOneInterval() {
        given(state.importedUntil(FRONTIERSAT)).willReturn(Optional.of(H18));
        given(client.observations(any(), any(), any(), any())).willThrow(new SatnogsThrottledException(null, null));

        satnogsImport.importAll();
        clock.now = NOW.plus(Duration.ofHours(1)).minusSeconds(1);
        satnogsImport.importAll();
        clock.now = NOW.plus(Duration.ofHours(1));
        satnogsImport.importAll();
        clock.now = NOW.plus(Duration.ofHours(2)).minusSeconds(1);
        satnogsImport.importAll();

        verify(client, times(2)).observations(any(), any(), any(), any());
    }

    @Test
    @Requirement("SAT-010")
    void defaultLookBackStartsTwoDaysAgo() {
        given(state.importedUntil(FRONTIERSAT)).willReturn(Optional.empty());
        given(client.observations(any(), any(), any(), any())).willThrow(new ResourceAccessException("down"));

        satnogsImport(List.of(69015), Duration.ofDays(2)).importAll();

        verify(client).observations(FRONTIERSAT, NOW.minus(Duration.ofDays(2)).minus(SatnogsImport.OVERLAP),
                NOW.minus(Duration.ofDays(2)).plus(SatnogsImport.CHUNK), null);
    }

    @Test
    @Requirement("SAT-010")
    void satelliteOutsideTheCatalogIsNotImported() {
        willThrow(new SatelliteNotInCatalogException(FRONTIERSAT)).given(catalog).ensureInCatalog(FRONTIERSAT);

        satnogsImport.importAll();

        verifyNoInteractions(client, state, reception);
    }

    @Test
    @Requirement("SAT-010")
    void importRunsAtStartAndThenEveryInterval() {
        satnogsImport.schedule();

        verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(NOW), eq(Duration.ofHours(1)));
    }

    @Test
    @Requirement("SAT-010")
    void withoutSatellitesNothingIsScheduled() {
        satnogsImport(List.of(), Duration.ofHours(3)).schedule();

        verifyNoInteractions(scheduler);
    }

    private SatnogsImport satnogsImport(List<Integer> satellites, Duration lookBack) {
        return new SatnogsImport(client, state, reception, catalog, new SatnogsImportProperties(satellites,
                Duration.ofHours(1), lookBack, Duration.ofHours(1)), scheduler, clock);
    }

    private void frames(SatnogsObservation... observations) {
        for (var observation : observations) {
            given(client.download(url(observation))).willReturn(FRAME);
        }
        given(reception.receive(any(), any(), any())).willReturn(new FrameReception.Outcome(1, true, true));
    }

    private static SatnogsObservation observation(long id, String end) {
        final var endTime = Instant.parse(end);
        return new SatnogsObservation(id, endTime.minusSeconds(600), endTime, "Station " + id, 25.0, 55.0, 32.0,
                List.of(new SatnogsObservation.Demoddata("https://s3.example/data_" + id + "_"
                        + endTime.minusSeconds(528).toString().replace(":", "-").replace("Z", ""))));
    }

    private static String url(SatnogsObservation observation) {
        return observation.demoddata().getFirst().payloadDemod();
    }

    private static final class SettableClock extends Clock {

        private Instant now;

        SettableClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
