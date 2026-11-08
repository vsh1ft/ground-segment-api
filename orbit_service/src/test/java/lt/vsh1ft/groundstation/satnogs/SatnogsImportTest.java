package lt.vsh1ft.groundstation.satnogs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
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
import lt.vsh1ft.groundstation.tmtc.FrameReception;
import lt.vsh1ft.groundstation.tmtc.Reception;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class SatnogsImportTest {

    private static final NoradId FRONTIERSAT = new NoradId(69015);
    private static final Instant NOW = Instant.parse("2026-11-08T20:00:00Z");
    private static final Instant SETTLED = NOW.minus(Duration.ofHours(1));
    private static final byte[] FRAME = {1, 2, 3};

    private final SatnogsNetworkClient client = mock(SatnogsNetworkClient.class);
    private final SatnogsImportState state = mock(SatnogsImportState.class);
    private final FrameReception reception = mock(FrameReception.class);
    private final SatelliteService catalog = mock(SatelliteService.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final SatnogsImport satnogsImport = satnogsImport(List.of(69015));

    @Test
    @Requirement("SAT-010")
    void firstImportGoesBackTwoDaysAndSkipsObservationsThatHaveNotSettled() {
        given(state.importedUntil(FRONTIERSAT)).willReturn(Optional.empty());
        final var recent = observation(3, NOW.minusSeconds(600), "recent");
        final var settled = observation(2, NOW.minus(Duration.ofHours(3)), "settled");
        final var tooOld = observation(1, NOW.minus(Duration.ofDays(3)), "old");
        given(client.observations(FRONTIERSAT, null)).willReturn(new SatnogsNetworkClient.Page(
                List.of(recent, settled, tooOld), "next"));
        frameDownloads(settled);

        satnogsImport.importAll();

        verify(reception).receive(FRONTIERSAT, FRAME, Reception.satnogs("Station settled", 25.0, 55.0, 32.0, 2,
                Instant.parse("2026-11-08T16:50:12Z")));
        verify(client, never()).download(url(recent));
        verify(client, never()).download(url(tooOld));
        verify(client, never()).observations(FRONTIERSAT, "next");
        verify(state).save(FRONTIERSAT, SETTLED);
    }

    @Test
    @Requirement("SAT-010")
    void laterImportContinuesFromWhereTheLastOneEndedAcrossPages() {
        final var lastEnd = NOW.minus(Duration.ofHours(5));
        given(state.importedUntil(FRONTIERSAT)).willReturn(Optional.of(lastEnd));
        final var onFirstPage = observation(5, NOW.minus(Duration.ofHours(2)), "first");
        final var onSecondPage = observation(4, NOW.minus(Duration.ofHours(4)), "second");
        final var alreadyImported = observation(3, lastEnd, "imported");
        given(client.observations(FRONTIERSAT, null)).willReturn(new SatnogsNetworkClient.Page(List.of(onFirstPage),
                "cursor"));
        given(client.observations(FRONTIERSAT, "cursor")).willReturn(new SatnogsNetworkClient.Page(
                List.of(onSecondPage, alreadyImported), null));
        frameDownloads(onFirstPage, onSecondPage);

        satnogsImport.importAll();

        verify(client).download(url(onFirstPage));
        verify(client).download(url(onSecondPage));
        verify(client, never()).download(url(alreadyImported));
        verify(state).save(FRONTIERSAT, SETTLED);
    }

    @Test
    @Requirement("SAT-010")
    void lastPageEndsTheImportEvenWithoutReachingTheLastImportedObservation() {
        given(state.importedUntil(FRONTIERSAT)).willReturn(Optional.of(NOW.minus(Duration.ofDays(1))));
        given(client.observations(FRONTIERSAT, null)).willReturn(new SatnogsNetworkClient.Page(List.of(), null));

        satnogsImport.importAll();

        verify(state).save(FRONTIERSAT, SETTLED);
    }

    @Test
    @Requirement("SAT-010")
    void frameFileWithoutATimeIsDatedByItsObservationStart() {
        given(state.importedUntil(FRONTIERSAT)).willReturn(Optional.empty());
        final var observation = new SatnogsObservation(7, Instant.parse("2026-11-08T16:40:00Z"),
                Instant.parse("2026-11-08T16:50:00Z"), "A", null, null, null,
                List.of(new SatnogsObservation.Demoddata("https://x/frame.bin")));
        given(client.observations(FRONTIERSAT, null)).willReturn(new SatnogsNetworkClient.Page(List.of(observation),
                null));
        given(client.download("https://x/frame.bin")).willReturn(FRAME);
        given(reception.receive(any(), any(), any())).willReturn(new FrameReception.Outcome(1, false, true));

        satnogsImport.importAll();

        verify(reception).receive(FRONTIERSAT, FRAME, Reception.satnogs("A", null, null, null, 7,
                Instant.parse("2026-11-08T16:40:00Z")));
    }

    @Test
    @Requirement("SAT-010")
    void failedFrameDownloadMakesTheNextImportStartFromTheSamePoint() {
        given(state.importedUntil(FRONTIERSAT)).willReturn(Optional.empty());
        final var observation = observation(2, NOW.minus(Duration.ofHours(3)), "settled");
        given(client.observations(FRONTIERSAT, null)).willReturn(new SatnogsNetworkClient.Page(List.of(observation),
                null));
        given(client.download(url(observation))).willThrow(new ResourceAccessException("timed out"));

        satnogsImport.importAll();

        verify(state, never()).save(any(), any());
    }

    @Test
    @Requirement("SAT-010")
    void unreachableSatnogsMakesTheNextImportStartFromTheSamePoint() {
        given(state.importedUntil(FRONTIERSAT)).willReturn(Optional.empty());
        given(client.observations(FRONTIERSAT, null)).willThrow(new ResourceAccessException("connection refused"));

        satnogsImport.importAll();

        verify(state, never()).save(any(), any());
    }

    @Test
    @Requirement("SAT-010")
    void throttledImportWaitsAsLongAsSatnogsAsks() {
        final var headers = new HttpHeaders();
        headers.set("Retry-After", "1048");
        given(state.importedUntil(FRONTIERSAT)).willReturn(Optional.empty());
        given(client.observations(FRONTIERSAT, null)).willThrow(HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", headers, new byte[0], StandardCharsets.UTF_8));
        final var clock = new SettableClock(NOW);
        final var throttled = new SatnogsImport(client, state, reception, catalog, new SatnogsImportProperties(
                List.of(69015), Duration.ofHours(1), Duration.ofDays(2), Duration.ofHours(1)), scheduler, clock);

        throttled.importAll();
        clock.now = NOW.plusSeconds(1047);
        throttled.importAll();
        clock.now = NOW.plusSeconds(1048);
        throttled.importAll();

        verify(client, times(2)).observations(FRONTIERSAT, null);
        verify(state, never()).save(any(), any());
    }

    @Test
    @Requirement("SAT-010")
    void throttledImportWithoutAWaitTimeWaitsOneInterval() {
        given(state.importedUntil(FRONTIERSAT)).willReturn(Optional.empty());
        given(client.observations(FRONTIERSAT, null)).willThrow(
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", null, null, null),
                tooManyRequests("soon"));
        final var clock = new SettableClock(NOW);
        final var throttled = new SatnogsImport(client, state, reception, catalog, new SatnogsImportProperties(
                List.of(69015), Duration.ofHours(1), Duration.ofDays(2), Duration.ofHours(1)), scheduler, clock);

        throttled.importAll();
        clock.now = NOW.plus(Duration.ofHours(1)).minusSeconds(1);
        throttled.importAll();
        clock.now = NOW.plus(Duration.ofHours(1));
        throttled.importAll();
        clock.now = NOW.plus(Duration.ofHours(2)).minusSeconds(1);
        throttled.importAll();

        verify(client, times(2)).observations(FRONTIERSAT, null);
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
        satnogsImport(List.of()).schedule();

        verifyNoInteractions(scheduler);
    }

    private static HttpClientErrorException tooManyRequests(String retryAfter) {
        final var headers = new HttpHeaders();
        headers.set("Retry-After", retryAfter);
        return HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", headers,
                new byte[0], StandardCharsets.UTF_8);
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

    private SatnogsImport satnogsImport(List<Integer> satellites) {
        return new SatnogsImport(client, state, reception, catalog, new SatnogsImportProperties(satellites,
                Duration.ofHours(1), Duration.ofDays(2), Duration.ofHours(1)), scheduler,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void frameDownloads(SatnogsObservation... observations) {
        for (var observation : observations) {
            given(client.download(url(observation))).willReturn(FRAME);
        }
        given(reception.receive(any(), any(), any())).willReturn(new FrameReception.Outcome(1, true, true));
    }

    private static SatnogsObservation observation(long id, Instant end, String name) {
        return new SatnogsObservation(id, end.minusSeconds(600), end, "Station " + name, 25.0, 55.0, 32.0,
                List.of(new SatnogsObservation.Demoddata(url(id, end))));
    }

    private static String url(SatnogsObservation observation) {
        return observation.demoddata().getFirst().payloadDemod();
    }

    private static String url(long id, Instant end) {
        return "https://s3.example/data_" + id + "_" + end.minusSeconds(588).toString().replace(":", "-")
                .replace("Z", "");
    }
}
