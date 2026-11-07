package lt.vsh1ft.groundstation.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;

class NoradIdTest {

    @Test
    @Requirement("API-030")
    void zeroIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NoradId(0));
    }

    @Test
    void sameNumberMeansSameSatellite() {
        assertThat(new NoradId(25544)).isEqualTo(new NoradId(25544));
    }
}
