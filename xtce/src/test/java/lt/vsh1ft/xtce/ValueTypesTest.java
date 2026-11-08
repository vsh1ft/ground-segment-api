package lt.vsh1ft.xtce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    void encodingSizeMustBeBetweenOneAndSixtyFourBits() {
        assertThatThrownBy(() -> new IntegerEncoding(0, false)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sizeInBits must be between 1 and 64, was 0");
        assertThatThrownBy(() -> new IntegerEncoding(65, false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new IntegerEncoding(64, true).littleEndian()).isFalse();
    }

    @Test
    void littleEndianEncodingMustBeWholeBytes() {
        assertThatThrownBy(() -> new IntegerEncoding(12, false, true)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a little-endian integer must be whole bytes, was 12 bits");
        assertThat(new IntegerEncoding(8, false, true).littleEndian()).isTrue();
    }

    @Test
    void stringIsAWholeNumberOfUnsignedBytesInOrder() {
        assertThatThrownBy(() -> new StringParameterType("s", new IntegerEncoding(12, false)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StringParameterType("s", new IntegerEncoding(16, true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StringParameterType("s", new IntegerEncoding(16, false, true)))
                .isInstanceOf(IllegalArgumentException.class);
        final var name = new StringParameterType("s", new IntegerEncoding(32, false));
        assertThat(name.unit()).isNull();
        assertThat(name.parse("CTS1")).isEqualTo(new StringValue("CTS1"));
        assertThat(name.monitor(0)).isEqualTo(MonitoringLevel.IN_LIMITS);
    }

    @Test
    void absoluteTimeNeedsAnEpochScaleAndOffset() {
        final var encoding = new IntegerEncoding(32, false);
        assertThatThrownBy(() -> new AbsoluteTimeParameterType("t", encoding, null, BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AbsoluteTimeParameterType("t", encoding, Instant.EPOCH, null, BigDecimal.ZERO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AbsoluteTimeParameterType("t", encoding, Instant.EPOCH, BigDecimal.ONE, null))
                .isInstanceOf(NullPointerException.class);
        final var time = new AbsoluteTimeParameterType("t", encoding, Instant.EPOCH, BigDecimal.ONE, BigDecimal.ZERO);
        assertThat(time.unit()).isNull();
        assertThat(time.parse("2026-11-08T13:51:24Z")).isEqualTo(new TimeValue(Instant.parse("2026-11-08T13:51:24Z")));
        assertThat(time.monitor(0)).isEqualTo(MonitoringLevel.IN_LIMITS);
    }

    @Test
    void textAndTimeValuesHaveNoNumber() {
        assertThat(new StringValue("CTS1").number()).isEmpty();
        assertThat(new TimeValue(Instant.EPOCH).number()).isEmpty();
    }

    @Test
    void entryCannotOverlapTheOneBeforeIt() {
        final var parameter = new Parameter("P", null, new StringParameterType("s", new IntegerEncoding(8, false)));

        assertThatThrownBy(() -> new SequenceContainer.Entry(parameter, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("an entry can't overlap the one before it, offset was -1");
        assertThat(new SequenceContainer.Entry(parameter).offsetInBits()).isZero();
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

        assertThat(new IntegerParameterType("t", null, encoding, AlarmRanges.NONE).parse("42"))
                .isEqualTo(new IntegerValue(42));
        assertThat(new FloatParameterType("f", null, encoding, PolynomialCalibrator.IDENTITY, AlarmRanges.NONE)
                .parse("0.5"))
                .isEqualTo(new FloatValue(0.5));
        assertThat(mode.parse("NOMINAL")).isEqualTo(new EnumeratedValue("NOMINAL"));
        assertThat(mode.unit()).isNull();
        assertThatThrownBy(() -> mode.parse("SAFE")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("not a label of mode_t");
    }

    @Test
    void integerAndFloatValuesAreNumbersAndEnumeratedValuesAreNot() {
        assertThat(new IntegerValue(42).number()).contains(42.0);
        assertThat(new FloatValue(7.2).number()).contains(7.2);
        assertThat(new EnumeratedValue("SAFE").number()).isEmpty();
    }

    @Test
    void integerValuesAreCheckedRawAndFloatValuesAfterCalibration() {
        final var encoding = new IntegerEncoding(16, false);
        final var limits = new AlarmRanges(new AlarmRange(10, true, 20, true), new AlarmRange(5, true, 25, true));
        final var counts = new IntegerParameterType("counts_t", null, encoding, limits);
        final var volts = new FloatParameterType("volts_t", "V", encoding, new PolynomialCalibrator(List.of(
                new PolynomialCalibrator.Term(1, 0.01))), limits);

        assertThat(counts.monitor(15)).isEqualTo(MonitoringLevel.IN_LIMITS);
        assertThat(counts.monitor(21)).isEqualTo(MonitoringLevel.WARNING);
        assertThat(volts.monitor(1500)).isEqualTo(MonitoringLevel.IN_LIMITS);
        assertThat(volts.monitor(2600)).isEqualTo(MonitoringLevel.CRITICAL);
    }

    @Test
    void enumeratedValuesAreAlwaysInLimitsAndAValueIsCheckedByItsParameterType() {
        final var encoding = new IntegerEncoding(8, false);
        final var mode = new EnumeratedParameterType("mode_t", encoding, Map.of(0L, "SAFE"), Map.of());
        final var limited = new Parameter("COUNT", null, new IntegerParameterType("count_t", null, encoding,
                new AlarmRanges(AlarmRange.UNBOUNDED, new AlarmRange(0, true, 9, true))));

        assertThat(mode.monitor(0)).isEqualTo(MonitoringLevel.IN_LIMITS);
        assertThat(new ParameterValue(limited, 10, Optional.of(new IntegerValue(10))).level())
                .isEqualTo(MonitoringLevel.CRITICAL);
    }
}
