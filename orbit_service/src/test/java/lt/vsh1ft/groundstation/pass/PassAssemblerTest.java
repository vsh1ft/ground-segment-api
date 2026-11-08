package lt.vsh1ft.groundstation.pass;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;

class PassAssemblerTest {

    private static final Instant T0 = Instant.parse("2026-10-31T00:00:00Z");

    private final PassAssembler assembler = new PassAssembler();

    private static Instant at(int minutes) {
        return T0.plusSeconds(minutes * 60L);
    }

    @Test
    @Requirement("PASS-030")
    void acquisitionMaximumLossMakesOnePassWithTheAzimuthOfEachMoment() {
        assembler.onAcquisition(at(0), 300.0);
        assembler.onMaximum(at(4), 36.9, 200.0);
        assembler.onLoss(at(8), 100.0);

        assertThat(assembler.passes()).containsExactly(new Pass(new Pass.Crossing(at(0), 300.0),
                new Pass.Crossing(at(8), 100.0), new Pass.Peak(at(4), 36.9, 200.0)));
    }

    @Test
    @Requirement("PASS-030")
    void passAlreadyInProgressAtStartIsSkipped() {
        assembler.onMaximum(at(1), 20.0, 200.0);
        assembler.onLoss(at(3), 100.0);
        assembler.onAcquisition(at(90), 300.0);
        assembler.onMaximum(at(94), 50.1, 200.0);
        assembler.onLoss(at(98), 100.0);

        assertThat(assembler.passes()).extracting(pass -> pass.start().time()).containsExactly(at(90));
    }

    @Test
    @Requirement("PASS-030")
    void passStillInProgressAtEndIsSkipped() {
        assembler.onAcquisition(at(0), 300.0);
        assembler.onMaximum(at(4), 13.9, 200.0);

        assertThat(assembler.passes()).isEmpty();
    }

    @Test
    @Requirement("PASS-030")
    void maximaBetweenPassesAreIgnored() {
        assembler.onMaximum(at(-40), -12.0, 200.0);
        assembler.onAcquisition(at(0), 300.0);
        assembler.onMaximum(at(4), 28.5, 200.0);
        assembler.onLoss(at(8), 100.0);
        assembler.onMaximum(at(50), -3.0, 200.0);

        assertThat(assembler.passes()).singleElement()
                .extracting(pass -> pass.peak().elevationDeg()).isEqualTo(28.5);
    }

    @Test
    @Requirement("PASS-030")
    void whenAPassHasTwoMaximaTheHigherOneIsThePeak() {
        assembler.onAcquisition(at(0), 300.0);
        assembler.onMaximum(at(2), 30.0, 200.0);
        assembler.onMaximum(at(5), 45.0, 200.0);
        assembler.onLoss(at(8), 100.0);

        assertThat(assembler.passes()).singleElement()
                .extracting(Pass::peak).isEqualTo(new Pass.Peak(at(5), 45.0, 200.0));
    }

    @Test
    @Requirement("PASS-030")
    void lowerSecondMaximumDoesNotReplaceThePeak() {
        assembler.onAcquisition(at(0), 300.0);
        assembler.onMaximum(at(2), 45.0, 200.0);
        assembler.onMaximum(at(5), 30.0, 200.0);
        assembler.onLoss(at(8), 100.0);

        assertThat(assembler.passes()).singleElement()
                .extracting(Pass::peak).isEqualTo(new Pass.Peak(at(2), 45.0, 200.0));
    }

    @Test
    @Requirement("PASS-030")
    void passWithoutAnyMaximumIsDropped() {
        assembler.onAcquisition(at(0), 300.0);
        assembler.onLoss(at(1), 100.0);

        assertThat(assembler.passes()).isEmpty();
    }

    @Test
    @Requirement("PASS-030")
    void consecutivePassesAreKeptInOrder() {
        assembler.onAcquisition(at(0), 300.0);
        assembler.onMaximum(at(4), 13.9, 200.0);
        assembler.onLoss(at(8), 100.0);
        assembler.onAcquisition(at(95), 300.0);
        assembler.onMaximum(at(99), 36.9, 200.0);
        assembler.onLoss(at(103), 100.0);

        assertThat(assembler.passes()).extracting(pass -> pass.start().time()).containsExactly(at(0), at(95));
    }

    @Test
    @Requirement("PASS-030")
    void noEventsMeansNoPasses() {
        assertThat(assembler.passes()).isEmpty();
    }
}
