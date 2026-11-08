package lt.vsh1ft.groundstation.contact;

import static lt.vsh1ft.groundstation.contact.ContactPlanTest.AOS;
import static lt.vsh1ft.groundstation.contact.ContactPlanTest.LOS;
import static lt.vsh1ft.groundstation.contact.ContactPlanTest.PASS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.util.Optional;

import lt.vsh1ft.groundstation.pass.Pass;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class LinkQualityTest {

    private static final NoradId SATELLITE = new NoradId(99999);
    private static final Optional<Pass> IN_PASS = Optional.of(PASS);

    private final LinkReportArchive reports = mock(LinkReportArchive.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final LinkQuality quality = new LinkQuality(reports,
            new ContactProperties(Duration.ofSeconds(10), Duration.ofMinutes(10), 0.05), events);

    @Test
    @Requirement("CON-050")
    void framesOutsideAPassAreInNoReport() {
        quality.frameReceived(SATELLITE, Optional.empty(), AOS, 3, false);
        quality.frameFailedCheck(SATELLITE, Optional.empty(), AOS);
        quality.check(Optional.empty(), AOS.plusSeconds(60));

        verifyNoInteractions(reports, events);
    }

    @Test
    @Requirement("CON-050")
    void reportOfThePassIsWrittenEveryTenSeconds() {
        quality.frameReceived(SATELLITE, IN_PASS, AOS, 0, true);
        quality.frameReceived(SATELLITE, IN_PASS, AOS.plusMillis(250), 2, false);
        quality.frameFailedCheck(SATELLITE, IN_PASS, AOS.plusMillis(500));

        quality.check(IN_PASS, AOS.plusSeconds(10).minusNanos(1));
        verifyNoInteractions(reports);

        quality.check(IN_PASS, AOS.plusSeconds(10));
        verify(reports).save(new LinkReport(99999, AOS, LOS, 2, 2, 1, 1, AOS.plusSeconds(10)));

        quality.check(IN_PASS, AOS.plusSeconds(19));
        quality.check(IN_PASS, AOS.plusSeconds(20));
        verify(reports, times(2)).save(any());
    }

    @Test
    @Requirement("CON-050")
    void lastReportIsWrittenWhenThePassEnds() {
        quality.frameReceived(SATELLITE, IN_PASS, AOS, 0, false);

        quality.check(Optional.empty(), LOS.plusMillis(400));
        quality.check(Optional.empty(), LOS.plusSeconds(20));

        verify(reports, times(1)).save(new LinkReport(99999, AOS, LOS, 1, 0, 0, 0, LOS));
    }

    @Test
    @Requirement("CON-050")
    void passPredictedAgainWithSlightlyDifferentTimesIsTheSameContact() {
        final var replanned = Optional.of(ContactPlanTest.pass(AOS.plusMillis(3), LOS.minusMillis(2)));
        quality.frameReceived(SATELLITE, IN_PASS, AOS, 0, false);

        quality.frameReceived(SATELLITE, replanned, AOS.plusSeconds(1), 0, false);
        quality.check(replanned, AOS.plusSeconds(10));

        verify(reports).save(new LinkReport(99999, AOS, LOS, 2, 0, 0, 0, AOS.plusSeconds(10)));
    }

    @Test
    @Requirement("CON-050")
    void frameOfTheNextPassClosesTheReportOfThePreviousOne() {
        final var next = ContactPlanTest.pass(AOS.plus(Duration.ofMinutes(95)), LOS.plus(Duration.ofMinutes(95)));
        quality.frameReceived(SATELLITE, IN_PASS, AOS, 0, false);

        quality.frameReceived(SATELLITE, Optional.of(next), next.start().time(), 0, false);

        verify(reports).save(new LinkReport(99999, AOS, LOS, 1, 0, 0, 0, LOS));
    }

    @Test
    @Requirement("CON-050")
    void passThatEndedBeforeTheTalliedOneStartedIsAnotherContact() {
        final var earlier = ContactPlanTest.pass(AOS.minus(Duration.ofMinutes(95)), LOS.minus(Duration.ofMinutes(95)));
        quality.frameReceived(SATELLITE, IN_PASS, AOS, 0, false);

        quality.check(Optional.of(earlier), AOS.plusSeconds(1));

        verify(reports).save(new LinkReport(99999, AOS, LOS, 1, 0, 0, 0, LOS));
    }

    @Test
    @Requirement("ALM-080")
    void lossAboveTheLimitDegradesTheLinkOnceAtLeast100FramesWereExpected() {
        frames(90, 0);
        frames(1, 8);

        quality.check(IN_PASS, AOS.plusSeconds(30));
        verify(events, never()).publishEvent(any());

        frames(1, 0);
        quality.check(IN_PASS, AOS.plusSeconds(31));
        quality.check(IN_PASS, AOS.plusSeconds(32));
        verify(events, times(1)).publishEvent(new LinkDegraded(SATELLITE, AOS.plusSeconds(31)));
    }

    @Test
    @Requirement("ALM-080")
    void lossOfExactlyTheLimitIsNotDegraded() {
        frames(94, 0);
        frames(1, 5);

        quality.check(IN_PASS, AOS.plusSeconds(30));

        verify(events, never()).publishEvent(any());
    }

    @Test
    @Requirement("ALM-080")
    void linkRecoversWhenTheLossFallsBackToTheLimit() {
        frames(90, 0);
        frames(1, 10);
        quality.check(IN_PASS, AOS.plusSeconds(30));

        frames(99, 0);
        quality.check(IN_PASS, AOS.plusSeconds(60));

        verify(events).publishEvent(new LinkDegraded(SATELLITE, AOS.plusSeconds(30)));
        verify(events).publishEvent(new LinkRecovered(SATELLITE, AOS.plusSeconds(60)));
    }

    @Test
    @Requirement("ALM-080")
    void degradedLinkRecoversAtTheEndOfThePass() {
        frames(80, 0);
        frames(1, 20);
        quality.check(IN_PASS, AOS.plusSeconds(30));

        quality.check(Optional.empty(), LOS);

        final var saved = ArgumentCaptor.forClass(LinkReport.class);
        verify(reports, times(2)).save(saved.capture());
        assertThat(saved.getValue().lossRatio()).isEqualTo(20.0 / 101);
        verify(events).publishEvent(new LinkRecovered(SATELLITE, LOS));
    }

    @Test
    void reportWithoutFramesHasNoLoss() {
        assertThat(new LinkReport(99999, AOS, LOS, 0, 0, 0, 0, AOS).lossRatio()).isZero();
    }

    private void frames(int count, int lostBefore) {
        for (var i = 0; i < count; i++) {
            quality.frameReceived(SATELLITE, IN_PASS, AOS.plusSeconds(1), lostBefore, false);
        }
    }
}
