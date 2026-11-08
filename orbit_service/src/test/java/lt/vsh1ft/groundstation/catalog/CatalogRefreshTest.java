package lt.vsh1ft.groundstation.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import lt.vsh1ft.groundstation.elements.CelestrakException;
import lt.vsh1ft.groundstation.elements.OmmArchive;
import lt.vsh1ft.groundstation.elements.OmmDownloader;
import lt.vsh1ft.groundstation.elements.OmmFixtures;
import lt.vsh1ft.groundstation.elements.SatelliteNotFoundException;
import lt.vsh1ft.groundstation.elements.StoredOmm;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

class CatalogRefreshTest {

    private static final StoredOmm ISS = OmmFixtures.ISS;
    private static final StoredOmm NOAA_19 = withNoradId(OmmFixtures.ISS, 33591);
    private static final StoredOmm METEOR_M2_3 = withNoradId(OmmFixtures.ISS, 57166);

    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
    private static final Duration INTERVAL = Duration.ofHours(4);

    private final OmmDownloader downloader = mock(OmmDownloader.class);
    private final OmmArchive archive = mock(OmmArchive.class);
    private final SatelliteService catalog = mock(SatelliteService.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CatalogRefresh refresh = new CatalogRefresh(downloader, archive, catalog,
            new CatalogRefreshProperties(INTERVAL, List.of("stations", "weather")), scheduler,
            Clock.fixed(NOW, ZoneOffset.UTC), events);

    @Test
    @Requirement("CAT-060")
    void firstRefreshRunsRightAwayWhenNothingWasDownloadedYet() {
        given(archive.lastDownload()).willReturn(Optional.empty());

        refresh.schedule();

        verify(scheduler).scheduleWithFixedDelay(any(), eq(NOW), eq(INTERVAL));
    }

    @Test
    @Requirement("CAT-060")
    void firstRefreshAfterARestartWaitsForTheRestOfTheInterval() {
        given(archive.lastDownload()).willReturn(Optional.of(NOW.minus(Duration.ofHours(1))));

        refresh.schedule();

        verify(scheduler).scheduleWithFixedDelay(any(), eq(NOW.plus(Duration.ofHours(3))), eq(INTERVAL));
    }

    @Test
    @Requirement("CAT-060")
    void firstRefreshRunsRightAwayWhenTheLastDownloadIsExactlyOneIntervalOld() {
        given(archive.lastDownload()).willReturn(Optional.of(NOW.minus(INTERVAL)));

        refresh.schedule();

        verify(scheduler).scheduleWithFixedDelay(any(), eq(NOW), eq(INTERVAL));
    }

    @Test
    @Requirement("CAT-060")
    void firstRefreshWaitsOneSecondWhenTheLastDownloadIsOneSecondYoungerThanTheInterval() {
        given(archive.lastDownload()).willReturn(Optional.of(NOW.minus(INTERVAL).plusSeconds(1)));

        refresh.schedule();

        verify(scheduler).scheduleWithFixedDelay(any(), eq(NOW.plusSeconds(1)), eq(INTERVAL));
    }

    @Test
    @Requirement("CAT-060")
    void firstRefreshRunsRightAwayWhenTheLastDownloadIsOlderThanTheInterval() {
        given(archive.lastDownload()).willReturn(Optional.of(NOW.minus(Duration.ofHours(5))));

        refresh.schedule();

        verify(scheduler).scheduleWithFixedDelay(any(), eq(NOW), eq(INTERVAL));
    }

    @Test
    @Requirement("CAT-050")
    void everySatelliteOfEveryGroupIsStored() {
        given(downloader.downloadGroup("stations")).willReturn(List.of(ISS));
        given(downloader.downloadGroup("weather")).willReturn(List.of(NOAA_19));

        refresh.refresh();

        verify(catalog).store(ISS);
        verify(catalog).store(NOAA_19);
    }

    @Test
    @Requirement("CAT-050")
    void catalogSatelliteOutsideTheGroupsIsDownloadedOnItsOwn() {
        given(downloader.downloadGroup("stations")).willReturn(List.of(ISS));
        given(downloader.downloadGroup("weather")).willReturn(List.of());
        given(catalog.all()).willReturn(List.of(noradId(ISS), noradId(METEOR_M2_3)));
        given(downloader.download(noradId(METEOR_M2_3))).willReturn(METEOR_M2_3);

        refresh.refresh();

        verify(catalog).store(METEOR_M2_3);
        verify(downloader, never()).download(noradId(ISS));
    }

    @Test
    @Requirement("CAT-070")
    void failingGroupDoesNotStopTheOthers() {
        given(downloader.downloadGroup("stations")).willThrow(new CelestrakException("CelesTrak is down"));
        given(downloader.downloadGroup("weather")).willReturn(List.of(NOAA_19));

        refresh.refresh();

        verify(catalog).store(NOAA_19);
    }

    @Test
    @Requirement("CAT-070")
    void satelliteThatFailsToStoreDoesNotStopTheRest() {
        given(downloader.downloadGroup("stations")).willReturn(List.of(ISS, NOAA_19));
        given(downloader.downloadGroup("weather")).willReturn(List.of());
        willThrow(new IllegalStateException("database is down")).given(catalog).store(ISS);

        refresh.refresh();

        verify(catalog).store(NOAA_19);
    }

    @Test
    @Requirement("CAT-070")
    void satelliteCelestrakNoLongerKnowsStaysInTheCatalog() {
        given(downloader.downloadGroup(any())).willReturn(List.of());
        given(catalog.all()).willReturn(List.of(noradId(METEOR_M2_3), noradId(NOAA_19)));
        given(downloader.download(noradId(METEOR_M2_3))).willThrow(new SatelliteNotFoundException(noradId(METEOR_M2_3)));
        given(downloader.download(noradId(NOAA_19))).willReturn(NOAA_19);

        refresh.refresh();

        verify(catalog).store(NOAA_19);
        verify(catalog, never()).delete(any());
    }

    @Test
    @Requirement("CAT-070")
    @ExtendWith(OutputCaptureExtension.class)
    void satelliteCelestrakDoesNotKnowIsNotAFailedRefresh(CapturedOutput output) {
        given(downloader.downloadGroup(any())).willReturn(List.of());
        given(catalog.all()).willReturn(List.of(new NoradId(99999), noradId(NOAA_19)));
        given(downloader.download(new NoradId(99999))).willThrow(new SatelliteNotFoundException(new NoradId(99999)));
        given(downloader.download(noradId(NOAA_19))).willThrow(new CelestrakException("CelesTrak is down"));

        refresh.refresh();

        assertThat(output).contains("NORAD ID 99999 is not on CelesTrak yet; it keeps its stored elements")
                .contains("0 satellites stored, 1 failures")
                .doesNotContain("Catalog refresh of NORAD ID 99999 failed");
    }

    @Test
    @Requirement("RAD-010")
    void finishedRefreshIsAnnouncedEvenWhenSomethingFailed() {
        given(downloader.downloadGroup(any())).willThrow(new CelestrakException("CelesTrak is down"));

        refresh.refresh();

        verify(events).publishEvent(new CatalogRefreshed());
    }

    private static NoradId noradId(StoredOmm omm) {
        return new NoradId(omm.noradId());
    }

    private static StoredOmm withNoradId(StoredOmm omm, int noradId) {
        return new StoredOmm(null, noradId, omm.objectName(), omm.objectId(), omm.epoch(),
                omm.meanMotion(), omm.eccentricity(), omm.inclination(), omm.raOfAscNode(),
                omm.argOfPericenter(), omm.meanAnomaly(), omm.ephemerisType(), omm.classificationType(),
                omm.elementSetNo(), omm.revAtEpoch(), omm.bstar(), omm.meanMotionDot(), omm.meanMotionDdot(),
                omm.downloadedAt());
    }
}
