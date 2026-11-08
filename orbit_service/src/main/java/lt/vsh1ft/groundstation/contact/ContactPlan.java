package lt.vsh1ft.groundstation.contact;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lt.vsh1ft.groundstation.pass.Pass;

record ContactPlan(boolean known, List<Pass> passes) {

    static final ContactPlan UNKNOWN = new ContactPlan(false, List.of());

    ContactPlan {
        passes = List.copyOf(passes);
    }

    static ContactPlan of(List<Pass> passes) {
        return new ContactPlan(true, passes);
    }

    Optional<Pass> passAt(Instant at) {
        return passes.stream()
                .filter(pass -> !at.isBefore(pass.start().time()) && at.isBefore(pass.end().time()))
                .findFirst();
    }

    Visibility visibility(Instant at) {
        if (!known) {
            return Visibility.UNKNOWN;
        }
        return passAt(at).isPresent() ? Visibility.IN_PASS : Visibility.OUTSIDE_PASS;
    }
}
