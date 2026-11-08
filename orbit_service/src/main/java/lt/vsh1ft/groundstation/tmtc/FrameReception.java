package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.xtce.DecodedPacket;
import lt.vsh1ft.xtce.MissionDatabase;
import lt.vsh1ft.xtce.PacketDecodingException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FrameReception {

    private final FrameArchive frames;
    private final ParameterArchive parameters;
    private final MissionDatabases databases;
    private final ApplicationEventPublisher publisher;

    @Transactional
    public Outcome receive(NoradId satellite, byte[] received, Reception reception) {
        final var checked = databases.frameCheck(satellite).check(received);
        final var frame = checked.content();
        final var known = frames.find(satellite, frame);
        final long frameId;
        final boolean newFrame;
        if (known.isPresent()) {
            frameId = known.get();
            newFrame = false;
        } else {
            final var stored = store(satellite, checked, reception.receivedAt());
            frameId = stored.frameId();
            newFrame = stored.isNew();
        }
        final var newReception = frames.addReception(frameId, reception);
        return new Outcome(frameId, newFrame, newReception);
    }

    private Stored store(NoradId satellite, FrameCheck.Checked checked, Instant receivedAt) {
        final var frame = checked.content();
        final Optional<DecodedPacket> decoded = databases.of(satellite)
                .flatMap(database -> decode(database, satellite, frame));
        final var generationTime = decoded.flatMap(DecodedPacket::generationTime).orElse(receivedAt);
        final var decoding = new FrameArchive.Decoding(decoded.map(packet -> packet.container().name()).orElse(null),
                decoded.flatMap(DecodedPacket::generationTime).orElse(null), checked.crcChecked());
        final var inserted = frames.insert(satellite, frame, receivedAt, decoding);
        if (inserted.isEmpty()) {
            return new Stored(frames.find(satellite, frame).orElseThrow(), false);
        }
        final long frameId = inserted.get();
        decoded.ifPresent(packet -> {
            parameters.storeFromFrame(satellite, frameId, generationTime, packet.values());
            publisher.publishEvent(new ParameterValuesReceived(satellite, frameId, generationTime, packet.values()));
        });
        return new Stored(frameId, true);
    }

    private static Optional<DecodedPacket> decode(MissionDatabase database, NoradId satellite, byte[] frame) {
        try {
            return Optional.of(database.decode(frame)).filter(DecodedPacket::complete);
        } catch (PacketDecodingException e) {
            log.warn("Frame of satellite {} not decoded with {}: {}", satellite, database.name(), e.getMessage());
            return Optional.empty();
        }
    }

    public record Outcome(long frameId, boolean newFrame, boolean newReception) {
    }

    private record Stored(long frameId, boolean isNew) {
    }
}
