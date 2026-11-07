package lt.vsh1ft.groundstation.pass;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class PassAssemblerTest {

    private static final Instant T0 = Instant.parse("2026-10-31T00:00:00Z");

    private final PassAssembler assembler = new PassAssembler();

    private static Instant at(int minutes) {
        return T0.plusSeconds(minutes * 60L);
    }

    @Test
    void acquisitionMaximumLossMakesOnePass() {
        assembler.onAcquisition(at(0));
        assembler.onMaximum(at(4), 36.9);
        assembler.onLoss(at(8));

        assertThat(assembler.passes()).containsExactly(new Pass(at(0), at(8), new Pass.Peak(at(4), 36.9)));
    }

    @Test
    void passAlreadyInProgressAtStartIsSkipped() {
        assembler.onMaximum(at(1), 20.0);
        assembler.onLoss(at(3));
        assembler.onAcquisition(at(90));
        assembler.onMaximum(at(94), 50.1);
        assembler.onLoss(at(98));

        assertThat(assembler.passes()).extracting(Pass::start).containsExactly(at(90));
    }

    @Test
    void passStillInProgressAtEndIsSkipped() {
        assembler.onAcquisition(at(0));
        assembler.onMaximum(at(4), 13.9);

        assertThat(assembler.passes()).isEmpty();
    }

    @Test
    void maximaBetweenPassesAreIgnored() {
        assembler.onMaximum(at(-40), -12.0);
        assembler.onAcquisition(at(0));
        assembler.onMaximum(at(4), 28.5);
        assembler.onLoss(at(8));
        assembler.onMaximum(at(50), -3.0);

        assertThat(assembler.passes()).singleElement()
                .extracting(pass -> pass.peak().elevationDeg()).isEqualTo(28.5);
    }

    @Test
    void whenAPassHasTwoMaximaTheHigherOneIsThePeak() {
        assembler.onAcquisition(at(0));
        assembler.onMaximum(at(2), 30.0);
        assembler.onMaximum(at(5), 45.0);
        assembler.onLoss(at(8));

        assertThat(assembler.passes()).singleElement()
                .extracting(Pass::peak).isEqualTo(new Pass.Peak(at(5), 45.0));
    }

    @Test
    void lowerSecondMaximumDoesNotReplaceThePeak() {
        assembler.onAcquisition(at(0));
        assembler.onMaximum(at(2), 45.0);
        assembler.onMaximum(at(5), 30.0);
        assembler.onLoss(at(8));

        assertThat(assembler.passes()).singleElement()
                .extracting(Pass::peak).isEqualTo(new Pass.Peak(at(2), 45.0));
    }

    @Test
    void passWithoutAnyMaximumIsDropped() {
        assembler.onAcquisition(at(0));
        assembler.onLoss(at(1));

        assertThat(assembler.passes()).isEmpty();
    }

    @Test
    void consecutivePassesAreKeptInOrder() {
        assembler.onAcquisition(at(0));
        assembler.onMaximum(at(4), 13.9);
        assembler.onLoss(at(8));
        assembler.onAcquisition(at(95));
        assembler.onMaximum(at(99), 36.9);
        assembler.onLoss(at(103));

        assertThat(assembler.passes()).extracting(Pass::start).containsExactly(at(0), at(95));
    }

    @Test
    void noEventsMeansNoPasses() {
        assertThat(assembler.passes()).isEmpty();
    }
}
