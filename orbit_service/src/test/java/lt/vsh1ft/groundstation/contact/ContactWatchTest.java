package lt.vsh1ft.groundstation.contact;

import static lt.vsh1ft.groundstation.contact.ContactPlanTest.AOS;
import static lt.vsh1ft.groundstation.contact.ContactPlanTest.LOS;
import static lt.vsh1ft.groundstation.contact.ContactPlanTest.PASS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;

class ContactWatchTest {

    private static final NoradId SATELLITE = new NoradId(99999);
    private static final Instant STARTED = Instant.parse("2026-11-08T09:00:00Z");
    private static final Duration THRESHOLD = Duration.ofSeconds(10);
    private static final Duration PLAN_INTERVAL = Duration.ofMinutes(10);
    private static final ContactPlan PLAN = ContactPlan.of(List.of(PASS));

    private final SettableClock clock = new SettableClock(STARTED);
    private final ContactPlanner planner = mock(ContactPlanner.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ContactWatch watch = new ContactWatch(planner, new ContactProperties(THRESHOLD, PLAN_INTERVAL),
            scheduler, clock, events);

    @Test
    @Requirement("CON-010")
    void watchedLinkIsPlannedAtOnceAndThenAtTheIntervalAndCheckedEverySecond() {
        given(planner.plan(SATELLITE, "Vilnius", STARTED)).willReturn(PLAN);

        watch.watch(SATELLITE, "Vilnius");

        verify(planner).plan(SATELLITE, "Vilnius", STARTED);
        verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(STARTED.plus(PLAN_INTERVAL)),
                eq(PLAN_INTERVAL));
        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofSeconds(1)));
        assertThat(watch.received(SATELLITE, AOS)).isEqualTo(Visibility.IN_PASS);
    }

    @Test
    @Requirement("CON-010")
    void newPlanReplacesTheOldOne() {
        watching(ContactPlan.UNKNOWN);
        clock.set(AOS.minusSeconds(60));
        given(planner.plan(SATELLITE, "Vilnius", AOS.minusSeconds(60))).willReturn(PLAN);

        watch.replan();

        assertThat(watch.received(SATELLITE, AOS)).isEqualTo(Visibility.IN_PASS);
    }

    @Test
    @Requirement("CON-020")
    void packetGetsTheVisibilityOfItsReceptionTimeInThePlan() {
        watching(PLAN);

        assertThat(watch.received(SATELLITE, AOS.minusSeconds(1))).isEqualTo(Visibility.OUTSIDE_PASS);
        assertThat(watch.received(SATELLITE, AOS)).isEqualTo(Visibility.IN_PASS);
        assertThat(watch.received(SATELLITE, LOS)).isEqualTo(Visibility.OUTSIDE_PASS);
    }

    @Test
    @Requirement("CON-020")
    void packetOfAnotherSatelliteOrBeforeTheWatchStartsIsUnknown() {
        assertThat(watch.received(SATELLITE, AOS)).isEqualTo(Visibility.UNKNOWN);
        watching(PLAN);

        assertThat(watch.received(new NoradId(25544), AOS)).isEqualTo(Visibility.UNKNOWN);
    }

    @Test
    @Requirement("CON-030")
    void silenceStartsWhenTheThresholdPassesAfterTheLastPacket() {
        watching(PLAN);
        watch.received(SATELLITE, AOS.plusSeconds(5));

        checkAt(AOS.plusSeconds(15).minusNanos(1));
        verifyNoInteractions(events);

        checkAt(AOS.plusSeconds(15));
        verify(events).publishEvent(new SilenceStarted(SATELLITE, AOS.plusSeconds(15)));
    }

    @Test
    @Requirement("CON-030")
    void passWithoutAnyPacketIsSilentOneThresholdAfterItStarts() {
        watching(PLAN);

        checkAt(AOS.plusSeconds(10).minusNanos(1));
        verifyNoInteractions(events);

        checkAt(AOS.plusSeconds(10));
        verify(events).publishEvent(new SilenceStarted(SATELLITE, AOS.plusSeconds(10)));
    }

    @Test
    @Requirement("CON-030")
    void watchStartedDuringAPassMeasuresSilenceFromItsOwnStart() {
        clock.set(AOS.plusSeconds(300));
        watching(PLAN);

        checkAt(AOS.plusSeconds(309));
        verifyNoInteractions(events);

        checkAt(AOS.plusSeconds(310));
        verify(events).publishEvent(new SilenceStarted(SATELLITE, AOS.plusSeconds(310)));
    }

    @Test
    @Requirement("CON-030")
    void silenceOutsideAPassIsNotNoticed() {
        watching(PLAN);

        checkAt(AOS.minusSeconds(1));
        checkAt(LOS);
        checkAt(LOS.plusSeconds(3600));

        verifyNoInteractions(events);
    }

    @Test
    @Requirement("CON-030")
    void silenceStartsOnceAndEndsWithTheNextPacket() {
        watching(PLAN);
        checkAt(AOS.plusSeconds(10));
        checkAt(AOS.plusSeconds(11));

        watch.received(SATELLITE, AOS.plusSeconds(30));
        watch.received(SATELLITE, AOS.plusSeconds(31));

        verify(events, times(1)).publishEvent(any(SilenceStarted.class));
        verify(events, times(1)).publishEvent(new SilenceEnded(SATELLITE, AOS.plusSeconds(30)));
    }

    @Test
    @Requirement("CON-030")
    void silenceEndsWhenThePassEnds() {
        watching(PLAN);
        checkAt(AOS.plusSeconds(10));

        checkAt(LOS.minusNanos(1));
        verify(events, never()).publishEvent(any(SilenceEnded.class));

        checkAt(LOS.plusMillis(300));
        checkAt(LOS.plusSeconds(1));
        verify(events, times(1)).publishEvent(new SilenceEnded(SATELLITE, LOS));
    }

    @Test
    @Requirement("CON-030")
    void silenceInTheNextPassStartsAgain() {
        final var next = ContactPlanTest.pass(AOS.plus(Duration.ofMinutes(95)), LOS.plus(Duration.ofMinutes(95)));
        watching(ContactPlan.of(List.of(PASS, next)));
        checkAt(AOS.plusSeconds(10));
        checkAt(LOS);

        checkAt(next.start().time().plusSeconds(10));

        verify(events).publishEvent(new SilenceStarted(SATELLITE, next.start().time().plusSeconds(10)));
    }

    private void watching(ContactPlan plan) {
        given(planner.plan(SATELLITE, "Vilnius", clock.instant())).willReturn(plan);
        watch.watch(SATELLITE, "Vilnius");
    }

    private void checkAt(Instant now) {
        clock.set(now);
        watch.check();
    }

    private static final class SettableClock extends Clock {

        private Instant now;

        SettableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
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
