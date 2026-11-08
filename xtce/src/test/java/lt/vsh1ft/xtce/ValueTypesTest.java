package lt.vsh1ft.xtce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ValueTypesTest {

    @Test
    void bitsAreReadMostSignificantFirstAcrossByteBoundaries() {
        final var reader = new BitReader(new byte[] {0x08, 0x64});

        assertThat(reader.read(5, false)).isEqualTo(0b00001);
        assertThat(reader.read(11, false)).isEqualTo(100);
    }

    @Test
    void signedFieldsUseTwosComplementForTheirOwnWidth() {
        final var reader = new BitReader(new byte[] {(byte) 0xFF, (byte) 0xCE, (byte) 0xE0, 0x7F, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF});

        assertThat(reader.read(16, true)).isEqualTo(-50);
        assertThat(reader.read(3, true)).isEqualTo(-1);
        assertThat(reader.read(5, true)).isEqualTo(0);
        assertThat(reader.read(32, true)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void thirtyTwoUnsignedBitsFitWithoutOverflow() {
        assertThat(new BitReader(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}).read(32, false))
                .isEqualTo(0xFFFF_FFFFL);
    }

    @Test
    void encodingSizeMustBeBetweenOneAndThirtyTwoBits() {
        assertThatThrownBy(() -> new IntegerEncoding(0, false)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sizeInBits must be between 1 and 32, was 0");
        assertThatThrownBy(() -> new IntegerEncoding(33, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void polynomialAddsEveryTerm() {
        final var kelvinToCelsius = new PolynomialCalibrator(List.of(
                new PolynomialCalibrator.Term(0, -273.15), new PolynomialCalibrator.Term(1, 0.01)));

        assertThat(kelvinToCelsius.apply(29_815)).isCloseTo(25.0, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(PolynomialCalibrator.IDENTITY.apply(-7)).isEqualTo(-7.0);
    }

    @Test
    void literalsAreParsedAsTheEngineeringValueOfTheirType() {
        final var encoding = new IntegerEncoding(8, false);
        final var mode = new EnumeratedParameterType("mode_t", encoding, Map.of(1L, "NOMINAL"), Map.of());

        assertThat(new IntegerParameterType("t", null, encoding).parse("42")).isEqualTo(new IntegerValue(42));
        assertThat(new FloatParameterType("f", null, encoding, PolynomialCalibrator.IDENTITY).parse("0.5"))
                .isEqualTo(new FloatValue(0.5));
        assertThat(mode.parse("NOMINAL")).isEqualTo(new EnumeratedValue("NOMINAL"));
        assertThat(mode.unit()).isNull();
        assertThatThrownBy(() -> mode.parse("SAFE")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("not a label of mode_t");
    }
}
