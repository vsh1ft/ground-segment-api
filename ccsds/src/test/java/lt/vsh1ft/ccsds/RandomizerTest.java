package lt.vsh1ft.ccsds;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class RandomizerTest {

    @Test
    void sequenceStartsAsPublishedInCcsds131() {
        assertThat(Randomizer.apply(new byte[10])).isEqualTo(Hex.bytes("FF 48 0E C0 9A 0D 70 BC 8E 2C"));
    }

    @Test
    void sequenceRepeatsEvery255Bytes() {
        final var sequence = Randomizer.apply(new byte[510]);

        assertThat(Arrays.copyOfRange(sequence, 255, 510)).isEqualTo(Arrays.copyOf(sequence, 255));
    }

    @Test
    void randomizingTwiceGivesBackTheOriginal() {
        final var frame = Hex.bytes("02 A0 14 05 18 19 00 00 55 55");

        assertThat(Randomizer.apply(Randomizer.apply(frame))).isEqualTo(frame);
        assertThat(Randomizer.apply(frame)).startsWith(Hex.bytes("FD E8 1A C5 82 14"));
    }
}
