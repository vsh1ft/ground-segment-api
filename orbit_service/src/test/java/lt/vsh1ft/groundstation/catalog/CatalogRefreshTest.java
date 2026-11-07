package lt.vsh1ft.groundstation.catalog;

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
import org.junit.jupiter.api.Test;
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
    private final CatalogRefresh refresh = new CatalogRefresh(downloader, archive, catalog,
            new CatalogRefreshProperties(INTERVAL, List.of("stations", "weather")), scheduler,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void firstRefreshRunsRightAwayWhenNothingWasDownloadedYet() {
        given(archive.lastDownload()).willReturn(Optional.empty());

        refresh.schedule();

        verify(scheduler).scheduleWithFixedDelay(any(), eq(NOW), eq(INTERVAL));
    }

    @Test
    void firstRefreshAfterARestartWaitsForTheRestOfTheInterval() {
        given(archive.lastDownload()).willReturn(Optional.of(NOW.minus(Duration.ofHours(1))));

        refresh.schedule();

        verify(scheduler).scheduleWithFixedDelay(any(), eq(NOW.plus(Duration.ofHours(3))), eq(INTERVAL));
    }

    @Test
    void firstRefreshRunsRightAwayWhenTheLastDownloadIsOlderThanTheInterval() {
        given(archive.lastDownload()).willReturn(Optional.of(NOW.minus(Duration.ofHours(5))));

        refresh.schedule();

        verify(scheduler).scheduleWithFixedDelay(any(), eq(NOW), eq(INTERVAL));
    }

    @Test
    void everySatelliteOfEveryGroupIsStored() {
        given(downloader.downloadGroup("stations")).willReturn(List.of(ISS));
        given(downloader.downloadGroup("weather")).willReturn(List.of(NOAA_19));

        refresh.refresh();

        verify(catalog).store(ISS);
        verify(catalog).store(NOAA_19);
    }

    @Test
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
    void failingGroupDoesNotStopTheOthers() {
        given(downloader.downloadGroup("stations")).willThrow(new CelestrakException("CelesTrak is down"));
        given(downloader.downloadGroup("weather")).willReturn(List.of(NOAA_19));

        refresh.refresh();

        verify(catalog).store(NOAA_19);
    }

    @Test
    void satelliteThatFailsToStoreDoesNotStopTheRest() {
        given(downloader.downloadGroup("stations")).willReturn(List.of(ISS, NOAA_19));
        given(downloader.downloadGroup("weather")).willReturn(List.of());
        willThrow(new IllegalStateException("database is down")).given(catalog).store(ISS);

        refresh.refresh();

        verify(catalog).store(NOAA_19);
    }

    @Test
    void satelliteCelestrakNoLongerKnowsStaysInTheCatalog() {
        given(downloader.downloadGroup(any())).willReturn(List.of());
        given(catalog.all()).willReturn(List.of(noradId(METEOR_M2_3), noradId(NOAA_19)));
        given(downloader.download(noradId(METEOR_M2_3))).willThrow(new SatelliteNotFoundException(noradId(METEOR_M2_3)));
        given(downloader.download(noradId(NOAA_19))).willReturn(NOAA_19);

        refresh.refresh();

        verify(catalog).store(NOAA_19);
        verify(catalog, never()).delete(any());
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
