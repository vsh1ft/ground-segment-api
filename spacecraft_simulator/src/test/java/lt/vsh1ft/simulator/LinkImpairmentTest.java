package lt.vsh1ft.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class LinkImpairmentTest {

    private static final byte[] CADU = new byte[68];

    @Test
    void perfectLinkPassesEveryFrameUnchanged() {
        final var link = new LinkImpairment(0, 0, new Random(1));

        assertThat(link.apply(CADU)).hasValueSatisfying(cadu -> assertThat(cadu).isEqualTo(CADU));
    }

    @Test
    void dropRateOfOneDropsEveryFrame() {
        assertThat(new LinkImpairment(1, 0, new Random(1)).apply(CADU)).isEmpty();
    }

    @Test
    void corruptedFrameHasExactlyOneBitFlippedOutsideTheSyncMarker() {
        final var link = new LinkImpairment(0, 1, new Random(3));

        for (var i = 0; i < 200; i++) {
            final var damaged = link.apply(CADU).orElseThrow();
            final var flipped = IntStream.range(0, damaged.length)
                    .map(index -> Integer.bitCount(Byte.toUnsignedInt(damaged[index]))).sum();
            assertThat(flipped).isEqualTo(1);
            assertThat(Arrays.copyOf(damaged, 4)).containsOnly(0);
        }
        assertThat(CADU).containsOnly(0);
    }

    @Test
    void ratesAreRoughlyTheShareOfFramesAffected() {
        final var link = new LinkImpairment(0.05, 0.03, new Random(11));
        var dropped = 0;
        var corrupted = 0;

        for (var i = 0; i < 10_000; i++) {
            final var result = link.apply(CADU);
            if (result.isEmpty()) {
                dropped++;
            } else if (!Arrays.equals(result.get(), CADU)) {
                corrupted++;
            }
        }

        assertThat(dropped).isBetween(400, 600);
        assertThat(corrupted).isBetween(200, 380);
    }
}
