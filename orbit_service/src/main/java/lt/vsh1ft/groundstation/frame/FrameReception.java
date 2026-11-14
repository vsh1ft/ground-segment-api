package lt.vsh1ft.groundstation.frame;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.tmtc.FrameCheck;
import lt.vsh1ft.groundstation.tmtc.MissionDatabases;
import lt.vsh1ft.groundstation.tmtc.ParameterValueReception;
import lt.vsh1ft.groundstation.tmtc.TelemetrySource;
import lt.vsh1ft.xtce.DecodedPacket;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FrameReception {

    private final FrameArchive frames;
    private final ParameterValueReception values;
    private final MissionDatabases databases;

    @Transactional
    public Outcome receive(NoradId satellite, byte[] received, Reception reception) {
        final var checked = databases.frameCheck(satellite).check(received);
        final var stored = frames.find(satellite, checked.content())
                .map(Stored::known)
                .orElseGet(() -> store(satellite, checked, reception.receivedAt()));
        final var newReception = frames.addReception(stored.frameId(), reception);
        return new Outcome(stored.frameId(), stored.isNew(), newReception);
    }

    private Stored store(NoradId satellite, FrameCheck.Checked checked, Instant receivedAt) {
        final var frame = checked.content();
        final var decoded = databases.decode(satellite, frame, "Frame").filter(DecodedPacket::complete);
        final var onBoardTime = decoded.flatMap(DecodedPacket::generationTime);
        final var decoding = new FrameArchive.Decoding(decoded.map(packet -> packet.container().name()).orElse(null),
                onBoardTime.orElse(null), checked.crcChecked());
        final var inserted = frames.insert(satellite, frame, receivedAt, decoding);
        if (inserted.isEmpty()) {
            return Stored.known(frames.find(satellite, frame).orElseThrow());
        }
        final long frameId = inserted.get();
        decoded.ifPresent(packet -> values.receive(satellite, TelemetrySource.frame(frameId),
                onBoardTime.orElse(receivedAt), packet.values()));
        return new Stored(frameId, true);
    }

    public record Outcome(long frameId, boolean newFrame, boolean newReception) {
    }

    private record Stored(long frameId, boolean isNew) {

        static Stored known(long frameId) {
            return new Stored(frameId, false);
        }
    }
}
