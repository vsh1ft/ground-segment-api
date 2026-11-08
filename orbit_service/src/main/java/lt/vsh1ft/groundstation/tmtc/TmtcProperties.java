package lt.vsh1ft.groundstation.tmtc;

import java.util.List;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("groundstation.tmtc")
record TmtcProperties(List<SatelliteDatabase> missionDatabases) {

    TmtcProperties {
        missionDatabases = missionDatabases == null ? List.of() : List.copyOf(missionDatabases);
    }

    record SatelliteDatabase(int noradId, Resource location, FrameCheck frameCheck) {

        SatelliteDatabase {
            Objects.requireNonNull(location, "groundstation.tmtc.mission-databases[].location is required");
            frameCheck = frameCheck == null ? FrameCheck.NONE : frameCheck;
        }
    }
}
