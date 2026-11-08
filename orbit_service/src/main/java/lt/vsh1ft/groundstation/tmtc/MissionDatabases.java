package lt.vsh1ft.groundstation.tmtc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.xtce.MissionDatabase;
import lt.vsh1ft.xtce.XtceReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class MissionDatabases {

    private final NoradId satellite;
    private final MissionDatabase database;

    MissionDatabases(TmLinkProperties link) {
        satellite = link.satellite();
        database = read(link.missionDatabase());
        log.info("Mission database {} for satellite {}: {} parameters", database.name(), satellite,
                database.parameters().size());
    }

    Optional<MissionDatabase> of(NoradId satellite) {
        return this.satellite.equals(satellite) ? Optional.of(database) : Optional.empty();
    }

    private static MissionDatabase read(Resource xtce) {
        try (var in = xtce.getInputStream()) {
            return XtceReader.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the mission database " + xtce, e);
        }
    }
}
