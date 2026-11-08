package lt.vsh1ft.groundstation.pass;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;

import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;

class PassTest {

    private static final Instant AOS = Instant.parse("2026-10-31T23:04:53Z");
    private static final Instant TCA = Instant.parse("2026-10-31T23:08:58Z");
    private static final Instant LOS = Instant.parse("2026-10-31T23:13:05Z");

    @Test
    @Requirement("PASS-030")
    void passEndingBeforeItStartsIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Pass(crossing(LOS), crossing(AOS), peak(TCA, 36.9)));
    }

    @Test
    @Requirement("PASS-030")
    void peakAfterTheEndOfThePassIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Pass(crossing(AOS), crossing(TCA), peak(LOS, 36.9)));
    }

    @Test
    @Requirement("PASS-030")
    void peakBeforeTheStartOfThePassIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Pass(crossing(TCA), crossing(LOS), peak(AOS, 36.9)));
    }

    @Test
    @Requirement("PASS-030")
    void passWithoutStartEndOrPeakIsRejected() {
        assertThatNullPointerException().isThrownBy(() -> new Pass(crossing(AOS), crossing(LOS), null));
        assertThatNullPointerException().isThrownBy(() -> new Pass(null, crossing(LOS), peak(TCA, 36.9)));
        assertThatNullPointerException().isThrownBy(() -> new Pass(crossing(AOS), null, peak(TCA, 36.9)));
    }

    @Test
    @Requirement("PASS-030")
    void peakElevationAbove90DegreesIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> peak(TCA, 90.5));
    }

    @Test
    @Requirement("PASS-030")
    void peakElevationBelowMinus90DegreesIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> peak(TCA, -90.5));
    }

    @Test
    @Requirement("PASS-030")
    void azimuthMustBeFromZeroUpToButExcluding360Degrees() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Pass.Crossing(AOS, 360.0));
        assertThatIllegalArgumentException().isThrownBy(() -> new Pass.Crossing(AOS, -0.01));
        assertThatIllegalArgumentException().isThrownBy(() -> new Pass.Peak(TCA, 36.9, 360.0));
        assertThatNullPointerException().isThrownBy(() -> new Pass.Crossing(null, 0.0));
    }

    private static Pass.Crossing crossing(Instant time) {
        return new Pass.Crossing(time, 180.0);
    }

    private static Pass.Peak peak(Instant time, double elevationDeg) {
        return new Pass.Peak(time, elevationDeg, 180.0);
    }
}
