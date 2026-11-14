package lt.vsh1ft.groundstation.tmtc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.xtce.DecodedPacket;
import lt.vsh1ft.xtce.MissionDatabase;
import lt.vsh1ft.xtce.PacketDecodingException;
import lt.vsh1ft.xtce.XtceReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MissionDatabases {

    private final Map<NoradId, MissionDatabase> databases = new HashMap<>();
    private final Map<NoradId, FrameCheck> frameChecks = new HashMap<>();

    MissionDatabases(TmLinkProperties link, TmtcProperties tmtc) {
        add(link.satellite(), link.missionDatabase(), FrameCheck.NONE);
        tmtc.missionDatabases().forEach(database ->
                add(new NoradId(database.noradId()), database.location(), database.frameCheck()));
    }

    Optional<MissionDatabase> of(NoradId satellite) {
        return Optional.ofNullable(databases.get(satellite));
    }

    public FrameCheck frameCheck(NoradId satellite) {
        return frameChecks.getOrDefault(satellite, FrameCheck.NONE);
    }

    public Optional<DecodedPacket> decode(NoradId satellite, byte[] content, String what) {
        return of(satellite).flatMap(database -> decode(database, satellite, content, what));
    }

    private static Optional<DecodedPacket> decode(MissionDatabase database, NoradId satellite, byte[] content,
                                                  String what) {
        try {
            return Optional.of(database.decode(content));
        } catch (PacketDecodingException e) {
            log.warn("{} of satellite {} not decoded with {}: {}", what, satellite, database.name(), e.getMessage());
            return Optional.empty();
        }
    }

    private void add(NoradId satellite, Resource xtce, FrameCheck frameCheck) {
        final var database = read(xtce);
        if (databases.putIfAbsent(satellite, database) != null) {
            throw new IllegalStateException("Satellite " + satellite + " has more than one mission database");
        }
        frameChecks.put(satellite, frameCheck);
        log.info("Mission database {} for satellite {}: {} parameters", database.name(), satellite,
                database.parameters().size());
    }

    private static MissionDatabase read(Resource xtce) {
        try (var in = xtce.getInputStream()) {
            return XtceReader.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the mission database " + xtce, e);
        }
    }
}
