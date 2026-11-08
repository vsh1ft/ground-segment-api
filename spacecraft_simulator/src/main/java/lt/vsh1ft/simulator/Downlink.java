package lt.vsh1ft.simulator;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Predicate;

import lt.vsh1ft.ccsds.FrameSynchronizer;
import lt.vsh1ft.ccsds.TmFrame;
import lt.vsh1ft.ccsds.TmPacket;
import lt.vsh1ft.ccsds.VirtualChannelFramer;

final class Downlink {

    static final int SPACECRAFT_ID = 42;
    static final int DATA_FIELD_LENGTH = 56;
    static final Duration FRAME_PERIOD = Duration.ofMillis(250);
    static final int HOUSEKEEPING_CHANNEL = 0;
    static final int EVENT_CHANNEL = 1;
    static final int IDLE_CHANNEL = 7;
    private static final int EVENT_SERVICE = 5;
    private static final System.Logger LOG = System.getLogger(Downlink.class.getName());

    private final TelemetryGenerator generator;
    private final Predicate<Instant> inView;
    private final LinkImpairment impairment;
    private final Consumer<byte[]> transmitter;
    private final VirtualChannelFramer housekeeping =
            new VirtualChannelFramer(SPACECRAFT_ID, HOUSEKEEPING_CHANNEL, DATA_FIELD_LENGTH);
    private final VirtualChannelFramer events =
            new VirtualChannelFramer(SPACECRAFT_ID, EVENT_CHANNEL, DATA_FIELD_LENGTH);
    private final VirtualChannelFramer idle = new VirtualChannelFramer(SPACECRAFT_ID, IDLE_CHANNEL, DATA_FIELD_LENGTH);
    private int masterChannelFrameCount;
    private Boolean visible;

    Downlink(TelemetryGenerator generator, Predicate<Instant> inView, LinkImpairment impairment,
             Consumer<byte[]> transmitter) {
        this.generator = generator;
        this.inView = inView;
        this.impairment = impairment;
        this.transmitter = transmitter;
    }

    void step(Instant now) {
        final var packets = generator.step(now);
        final boolean nowVisible = inView.test(now);
        if (visible == null || nowVisible != visible) {
            LOG.log(System.Logger.Level.INFO, nowVisible ? "In view of the station at {0}; transmitting"
                    : "Out of view of the station at {0}; not transmitting", now);
            visible = nowVisible;
        }
        if (!nowVisible) {
            housekeeping.clear();
            events.clear();
            return;
        }
        packets.forEach(packet -> channelOf(packet).add(packet.encode()));
    }

    void frameSlot() {
        if (!Boolean.TRUE.equals(visible)) {
            return;
        }
        final var count = masterChannelFrameCount;
        masterChannelFrameCount = (count + 1) & TmFrame.MAX_FRAME_COUNT;
        impairment.apply(FrameSynchronizer.cadu(nextFrame(count).encode())).ifPresent(transmitter);
    }

    private TmFrame nextFrame(int count) {
        if (!events.isEmpty()) {
            return events.paddedFrame(count);
        }
        if (housekeeping.hasFullFrame()) {
            return housekeeping.fullFrame(count);
        }
        return idle.idleFrame(count);
    }

    private VirtualChannelFramer channelOf(TmPacket packet) {
        return packet.secondaryHeader().messageType().serviceType() == EVENT_SERVICE ? events : housekeeping;
    }
}
