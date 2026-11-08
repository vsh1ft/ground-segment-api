package lt.vsh1ft.simulator;

import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Predicate;

final class Downlink {

    private static final System.Logger LOG = System.getLogger(Downlink.class.getName());

    private final TelemetryGenerator generator;
    private final Predicate<Instant> inView;
    private final Consumer<byte[]> transmitter;
    private Boolean wasInView;

    Downlink(TelemetryGenerator generator, Predicate<Instant> inView, Consumer<byte[]> transmitter) {
        this.generator = generator;
        this.inView = inView;
        this.transmitter = transmitter;
    }

    void step(Instant now) {
        final var packets = generator.step(now);
        final boolean visible = inView.test(now);
        if (wasInView == null || visible != wasInView) {
            LOG.log(System.Logger.Level.INFO, visible ? "In view of the station at {0}; transmitting"
                    : "Out of view of the station at {0}; not transmitting", now);
            wasInView = visible;
        }
        if (visible) {
            packets.forEach(packet -> transmitter.accept(packet.encode()));
        }
    }
}
