package lt.vsh1ft.groundstation.tmtc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import lt.vsh1ft.xtce.InvalidMissionDatabaseException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class MissionDatabasesTest {

    @Test
    @Requirement("PAR-010")
    void simulatorDatabaseIsLoadedForTheLinksSatelliteOnly() {
        final var databases = SimulatorTelemetry.databases();

        assertThat(databases.of(SimulatorTelemetry.SATELLITE)).hasValueSatisfying(database -> {
            assertThat(database.name()).isEqualTo("SIMULATOR");
            assertThat(database.parameter("BATTERY_VOLTAGE")).isPresent();
        });
        assertThat(databases.of(new NoradId(25544))).isEmpty();
    }

    @Test
    @Requirement("PAR-010")
    void missingFileStopsTheStartup() {
        final var link = SimulatorTelemetry.link(new ClassPathResource("mdb/missing.xml"));

        assertThatThrownBy(() -> new MissionDatabases(link)).isInstanceOf(UncheckedIOException.class)
                .hasMessageStartingWith("Cannot read the mission database class path resource [mdb/missing.xml]");
    }

    @Test
    @Requirement("PAR-010")
    void fileOutsideTheSupportedXtceSubsetStopsTheStartup() {
        final var xtce = """
                <SpaceSystem xmlns="http://www.omg.org/spec/XTCE/20180204" name="BAD">
                  <TelemetryMetaData><ParameterTypeSet>
                    <StringParameterType name="text_t"/>
                  </ParameterTypeSet></TelemetryMetaData>
                </SpaceSystem>""";
        final var link = SimulatorTelemetry.link(new ByteArrayResource(xtce.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> new MissionDatabases(link)).isInstanceOf(InvalidMissionDatabaseException.class)
                .hasMessage("Unsupported XTCE element StringParameterType 'text_t'");
    }
}
