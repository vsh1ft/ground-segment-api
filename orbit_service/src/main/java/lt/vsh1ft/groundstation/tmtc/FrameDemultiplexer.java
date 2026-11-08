package lt.vsh1ft.groundstation.tmtc;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.ccsds.MalformedFrameException;
import lt.vsh1ft.ccsds.PacketExtractor;
import lt.vsh1ft.ccsds.TmFrame;
import lt.vsh1ft.groundstation.contact.ContactWatch;
import lt.vsh1ft.groundstation.shared.NoradId;

@Slf4j
final class FrameDemultiplexer {

    static final Duration COUNTED_GAP = Duration.ofSeconds(30);

    private final NoradId satellite;
    private final int spacecraftId;
    private final TelemetryReception reception;
    private final ContactWatch contacts;
    private final Map<Integer, PacketExtractor> channels = new HashMap<>();
    private int lastFrameCount = -1;
    private Instant lastFrameAt;

    FrameDemultiplexer(NoradId satellite, int spacecraftId, TelemetryReception reception, ContactWatch contacts) {
        this.satellite = satellite;
        this.spacecraftId = spacecraftId;
        this.reception = reception;
        this.contacts = contacts;
    }

    void receive(byte[] bytes, Instant receivedAt) {
        final TmFrame frame;
        try {
            frame = TmFrame.decode(bytes);
        } catch (MalformedFrameException e) {
            log.debug("Frame of satellite {} dropped: {}", satellite, e.getMessage());
            contacts.frameFailedCheck(satellite, receivedAt);
            return;
        }
        if (frame.spacecraftId() != spacecraftId) {
            log.debug("Frame of spacecraft {} dropped; the link of satellite {} receives spacecraft {}",
                    frame.spacecraftId(), satellite, spacecraftId);
            return;
        }
        contacts.frameReceived(satellite, receivedAt, lostBefore(frame, receivedAt), frame.idle());
        channels.computeIfAbsent(frame.virtualChannel(), this::extractor).accept(frame)
                .forEach(packet -> reception.receive(satellite, packet, receivedAt));
    }

    private int lostBefore(TmFrame frame, Instant receivedAt) {
        final var counted = lastFrameCount >= 0
                && Duration.between(lastFrameAt, receivedAt).compareTo(COUNTED_GAP) < 0;
        final var lost = counted ? (frame.masterChannelFrameCount() - lastFrameCount - 1) & TmFrame.MAX_FRAME_COUNT : 0;
        lastFrameCount = frame.masterChannelFrameCount();
        lastFrameAt = receivedAt;
        return lost;
    }

    private PacketExtractor extractor(int virtualChannel) {
        return new PacketExtractor(reason ->
                log.warn("Virtual channel {} of satellite {}: {}", virtualChannel, satellite, reason));
    }
}
