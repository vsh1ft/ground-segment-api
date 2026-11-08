package lt.vsh1ft.ccsds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CucTimeTest {

    @Test
    void timeIsWrittenAsFourBytesOfSecondsAndTwoBytesOfFraction() {
        final var buffer = ByteBuffer.allocate(CucTime.LENGTH);

        new CucTime(0x12345678L, 0x8000).writeTo(buffer);

        assertThat(buffer.array()).isEqualTo(Hex.bytes("12 34 56 78 80 00"));
    }

    @Test
    void largestTimeIsReadAsUnsignedValues() {
        final var time = CucTime.readFrom(ByteBuffer.wrap(Hex.bytes("FF FF FF FF FF FF")));

        assertThat(time).isEqualTo(new CucTime(4_294_967_295L, 65_535));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 4_294_967_296L})
    void coarseTimeOutsideThirtyTwoBitsIsRejected(long coarse) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CucTime(coarse, 0))
                .withMessage("CUC coarse time must be between 0 and 4294967295, was " + coarse);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 65_536})
    void fineTimeOutsideSixteenBitsIsRejected(int fine) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CucTime(0, fine))
                .withMessage("CUC fine time must be between 0 and 65535, was " + fine);
    }
}
