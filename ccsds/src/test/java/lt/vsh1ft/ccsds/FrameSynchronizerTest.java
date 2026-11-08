package lt.vsh1ft.ccsds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class FrameSynchronizerTest {

    private static final byte[] FIRST = new TmFrame(42, 0, 0, 0, 0, new byte[56]).encode();
    private static final byte[] SECOND = new TmFrame(42, 7, 1, 0, TmFrame.IDLE_DATA_ONLY, new byte[56]).encode();

    @Test
    void caduIsTheMarkerFollowedByTheRandomizedFrame() {
        final var cadu = FrameSynchronizer.cadu(FIRST);

        assertThat(cadu).hasSize(68).startsWith(Hex.bytes("1A CF FC 1D FD E8 0E C0 82 0D"));
        assertThat(Randomizer.apply(Arrays.copyOfRange(cadu, 4, 68))).isEqualTo(FIRST);
    }

    @Test
    void framesBackToBackAreReadAndDerandomized() throws IOException {
        final var synchronizer = synchronizer(FrameSynchronizer.cadu(FIRST), FrameSynchronizer.cadu(SECOND));

        assertThat(synchronizer.next()).contains(FIRST);
        assertThat(synchronizer.next()).contains(SECOND);
        assertThat(synchronizer.next()).isEmpty();
        assertThat(synchronizer.skippedBytes()).isZero();
    }

    @Test
    void bytesBeforeTheFirstMarkerAreSkipped() throws IOException {
        final var synchronizer = synchronizer(Hex.bytes("1A CF 00 1A CF FC"), FrameSynchronizer.cadu(FIRST));

        assertThat(synchronizer.next()).contains(FIRST);
        assertThat(synchronizer.skippedBytes()).isEqualTo(6);
    }

    @Test
    void missingMarkerAfterAFrameStartsASearchForTheNextOne() throws IOException {
        final var damaged = FrameSynchronizer.cadu(SECOND);
        damaged[1] = 0;
        final var synchronizer = synchronizer(FrameSynchronizer.cadu(FIRST), damaged, FrameSynchronizer.cadu(FIRST));

        assertThat(synchronizer.next()).contains(FIRST);
        assertThat(synchronizer.next()).contains(FIRST);
        assertThat(synchronizer.skippedBytes()).isEqualTo(68);
    }

    @Test
    void streamEndingInsideAFrameOrAMarkerHasNoMoreFrames() throws IOException {
        final var cadu = FrameSynchronizer.cadu(FIRST);

        assertThat(synchronizer(Arrays.copyOf(cadu, 30)).next()).isEmpty();
        final var endsInMarker = synchronizer(cadu, Hex.bytes("1A CF"));
        assertThat(endsInMarker.next()).contains(FIRST);
        assertThat(endsInMarker.next()).isEmpty();
        assertThat(synchronizer(new byte[0]).next()).isEmpty();
    }

    @Test
    void streamWithoutAMarkerHasNoFrames() throws IOException {
        final var synchronizer = synchronizer(new byte[100]);

        assertThat(synchronizer.next()).isEmpty();
        assertThat(synchronizer.skippedBytes()).isEqualTo(96);
    }

    @Test
    void frameMustBeLongEnoughForAHeaderOneByteAndErrorControl() {
        final var input = new ByteArrayInputStream(new byte[0]);

        assertThatIllegalArgumentException().isThrownBy(() -> new FrameSynchronizer(input, 8));
        assertThat(new FrameSynchronizer(input, 9).skippedBytes()).isZero();
    }

    private static FrameSynchronizer synchronizer(byte[]... parts) {
        final var stream = new ByteArrayOutputStream();
        for (final var part : parts) {
            stream.writeBytes(part);
        }
        return new FrameSynchronizer(new ByteArrayInputStream(stream.toByteArray()), 64);
    }
}
