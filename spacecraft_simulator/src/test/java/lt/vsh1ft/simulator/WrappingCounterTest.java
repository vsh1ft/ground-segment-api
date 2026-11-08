package lt.vsh1ft.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class WrappingCounterTest {

    @Test
    void counterStartsAtZeroAndWrapsAfterItsMaximum() {
        final var counter = new WrappingCounter(2);

        assertThat(IntStream.range(0, 5).map(i -> counter.next())).containsExactly(0, 1, 2, 0, 1);
    }
}
