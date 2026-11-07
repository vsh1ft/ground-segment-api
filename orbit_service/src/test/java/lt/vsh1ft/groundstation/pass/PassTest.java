package lt.vsh1ft.groundstation.pass;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class PassTest {

    private static final Instant AOS = Instant.parse("2026-10-31T23:04:53Z");
    private static final Instant TCA = Instant.parse("2026-10-31T23:08:58Z");
    private static final Instant LOS = Instant.parse("2026-10-31T23:13:05Z");

    @Test
    void passEndingBeforeItStartsIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Pass(LOS, AOS, new Pass.Peak(TCA, 36.9)));
    }

    @Test
    void peakAfterTheEndOfThePassIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Pass(AOS, TCA, new Pass.Peak(LOS, 36.9)));
    }

    @Test
    void peakBeforeTheStartOfThePassIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Pass(TCA, LOS, new Pass.Peak(AOS, 36.9)));
    }

    @Test
    void passWithoutPeakIsRejected() {
        assertThatNullPointerException().isThrownBy(() -> new Pass(AOS, LOS, null));
    }

    @Test
    void peakElevationAbove90DegreesIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Pass.Peak(TCA, 90.5));
    }

    @Test
    void peakElevationBelowMinus90DegreesIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Pass.Peak(TCA, -90.5));
    }
}
