package lt.vsh1ft.groundstation.tmtc;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.xtce.MissionDatabase;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class ParameterService {

    private final MissionDatabases databases;
    private final ParameterArchive archive;

    List<ParameterResponse> parameters(NoradId satellite) {
        return database(satellite).parameters().stream().map(ParameterResponse::from).toList();
    }

    ParameterValuesResponse values(NoradId satellite, String name, ValuesQuery query) {
        final var parameter = database(satellite).parameter(name)
                .orElseThrow(() -> new UnknownParameterException(satellite, name));
        return archive.values(satellite, parameter, query);
    }

    private MissionDatabase database(NoradId satellite) {
        return databases.of(satellite).orElseThrow(() -> new NoMissionDatabaseException(satellite));
    }
}
