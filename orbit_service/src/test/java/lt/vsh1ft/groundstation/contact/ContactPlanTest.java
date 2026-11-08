package lt.vsh1ft.groundstation.contact;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lt.vsh1ft.groundstation.pass.Pass;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;

class ContactPlanTest {

    static final Instant AOS = Instant.parse("2026-11-08T09:55:12Z");
    static final Instant LOS = Instant.parse("2026-11-08T10:06:48Z");
    static final Pass PASS = pass(AOS, LOS);

    private final ContactPlan plan = ContactPlan.of(List.of(PASS));

    @Test
    @Requirement("CON-020")
    void passStartsAtAcquisitionAndEndsJustBeforeLoss() {
        assertThat(plan.visibility(AOS.minusNanos(1))).isEqualTo(Visibility.OUTSIDE_PASS);
        assertThat(plan.visibility(AOS)).isEqualTo(Visibility.IN_PASS);
        assertThat(plan.visibility(LOS.minusNanos(1))).isEqualTo(Visibility.IN_PASS);
        assertThat(plan.visibility(LOS)).isEqualTo(Visibility.OUTSIDE_PASS);
        assertThat(plan.passAt(AOS)).contains(PASS);
        assertThat(plan.passAt(LOS)).isEmpty();
    }

    @Test
    @Requirement("CON-020")
    void unknownPlanHasNoPassesAndUnknownVisibility() {
        assertThat(ContactPlan.UNKNOWN.visibility(AOS)).isEqualTo(Visibility.UNKNOWN);
        assertThat(ContactPlan.UNKNOWN.passAt(AOS)).isEmpty();
    }

    @Test
    void planWithoutPassesIsKnownAndEverythingIsOutsideAPass() {
        assertThat(ContactPlan.of(List.of()).visibility(AOS)).isEqualTo(Visibility.OUTSIDE_PASS);
    }

    @Test
    void planKeepsItsOwnCopyOfThePasses() {
        final var passes = new ArrayList<>(List.of(PASS));
        final var copied = ContactPlan.of(passes);

        passes.clear();

        assertThat(copied.passes()).containsExactly(PASS);
    }

    static Pass pass(Instant aos, Instant los) {
        return new Pass(new Pass.Crossing(aos, 200.0), new Pass.Crossing(los, 20.0),
                new Pass.Peak(aos.plus(Duration.between(aos, los).dividedBy(2)), 40.0, 110.0));
    }
}
