package lt.vsh1ft.ccsds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApidTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 100, 2047})
    void apidAcceptsElevenBitValues(int value) {
        assertThat(new Apid(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2048})
    void apidRejectsValuesOutsideElevenBits(int value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Apid(value))
                .withMessage("APID must be between 0 and 2047, was " + value);
    }
}
