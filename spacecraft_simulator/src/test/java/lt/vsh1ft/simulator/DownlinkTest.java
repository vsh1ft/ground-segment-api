package lt.vsh1ft.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import lt.vsh1ft.ccsds.FrameSynchronizer;
import lt.vsh1ft.ccsds.MessageType;
import lt.vsh1ft.ccsds.PacketExtractor;
import lt.vsh1ft.ccsds.Randomizer;
import lt.vsh1ft.ccsds.TmFrame;
import lt.vsh1ft.ccsds.TmPacket;
import org.junit.jupiter.api.Test;

class DownlinkTest {

    private static final Instant START = Instant.parse("2026-11-08T00:00:00Z");
    private static final Duration ORBIT = Duration.ofMinutes(92);
    private static final LinkImpairment PERFECT = new LinkImpairment(0, 0, new Random(1));

    private final List<byte[]> sent = new ArrayList<>();

    @Test
    void everyFrameSlotInViewSendsOneCaduOfThisSpacecraft() {
        final var downlink = downlink(at -> true, PERFECT);

        run(downlink, START, 10);

        assertThat(sent).hasSize(40).allSatisfy(cadu -> assertThat(cadu).hasSize(68));
        assertThat(frames()).extracting(TmFrame::spacecraftId).containsOnly(42);
        assertThat(frames()).extracting(TmFrame::masterChannelFrameCount).startsWith(0, 1, 2, 3);
    }

    @Test
    void housekeepingFillsWholeVc0FramesAndIdleFramesFillTheRest() {
        final var downlink = downlink(at -> true, PERFECT);

        run(downlink, START, 10);

        final var housekeepingFrames = frames().stream().filter(frame -> frame.virtualChannel() == 0).toList();
        assertThat(housekeepingFrames).hasSize(10 * 27 / 56);
        assertThat(frames()).filteredOn(TmFrame::idle).extracting(TmFrame::virtualChannel).containsOnly(7);
        assertThat(packets(housekeepingFrames)).hasSize(4 * 56 / 27).extracting(packet -> packet.secondaryHeader().time())
                .doesNotHaveDuplicates();
    }

    @Test
    void eventsGoOnVc1InTheNextFrameSlot() {
        final var downlink = downlink(at -> true, PERFECT);
        final var lowBattery = START.plus(Duration.ofMinutes(69));

        downlink.step(lowBattery);
        downlink.frameSlot();

        final var frame = frames().getFirst();
        assertThat(frame.virtualChannel()).isEqualTo(1);
        assertThat(packets(List.of(frame))).extracting(packet -> packet.secondaryHeader().messageType())
                .containsExactly(MessageType.LOW_SEVERITY_ANOMALY_REPORT, MessageType.HIGH_SEVERITY_ANOMALY_REPORT);
    }

    @Test
    void nothingIsSentOutOfViewAndWhatWaitedIsDropped() {
        final var losAt = START.plusSeconds(3);
        final var aosAt = START.plusSeconds(6);
        final var downlink = downlink(at -> at.isBefore(losAt) || !at.isBefore(aosAt), PERFECT);

        run(downlink, START, 3);
        final var beforeLos = sent.size();
        run(downlink, losAt, 3);
        final var whileOutOfView = sent.size() - beforeLos;
        run(downlink, aosAt, 1);

        assertThat(whileOutOfView).isZero();
        assertThat(frames().getLast().idle()).isTrue();
    }

    @Test
    void nothingIsSentBeforeTheFirstStep() {
        downlink(at -> true, PERFECT).frameSlot();

        assertThat(sent).isEmpty();
    }

    @Test
    void droppedFramesStillAdvanceTheMasterChannelFrameCount() {
        final var downlink = downlink(at -> true, new LinkImpairment(0.5, 0, new Random(7)));

        run(downlink, START, 10);

        assertThat(sent).hasSizeBetween(5, 35);
        assertThat(frames().getLast().masterChannelFrameCount()).isGreaterThan(sent.size() - 1);
    }

    private Downlink downlink(Predicate<Instant> inView, LinkImpairment impairment) {
        return new Downlink(new TelemetryGenerator(new Spacecraft(START, ORBIT)), inView, impairment, sent::add);
    }

    private static void run(Downlink downlink, Instant from, int seconds) {
        for (var second = 0; second < seconds; second++) {
            downlink.step(from.plusSeconds(second));
            for (var slot = 0; slot < 4; slot++) {
                downlink.frameSlot();
            }
        }
    }

    private List<TmFrame> frames() {
        return sent.stream()
                .map(cadu -> TmFrame.decode(Randomizer.apply(Arrays.copyOfRange(cadu,
                        FrameSynchronizer.MARKER_LENGTH, cadu.length))))
                .toList();
    }

    private static List<TmPacket> packets(List<TmFrame> frames) {
        final var extractor = new PacketExtractor(reason -> {
            throw new AssertionError(reason);
        });
        return frames.stream().flatMap(frame -> extractor.accept(frame).stream()).toList();
    }
}
