package lt.vsh1ft.groundstation.tmtc;

import static lt.vsh1ft.groundstation.tmtc.SimulatorTelemetry.SATELLITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;

class ParameterServiceTest {

    private static final ValuesQuery DEFAULTS = new ValuesQuery(null, null, null, null);

    private final MissionDatabases databases = SimulatorTelemetry.databases();
    private final ParameterArchive archive = mock(ParameterArchive.class);
    private final ParameterService service = new ParameterService(databases, archive);

    @Test
    @Requirement("PAR-040")
    void everyParameterOfTheMissionDatabaseIsListedInDatabaseOrder() {
        assertThat(service.parameters(SATELLITE)).hasSize(22).first()
                .extracting(ParameterResponse::name).isEqualTo("PACKET_VERSION");
    }

    @Test
    @Requirement("PAR-050")
    void valuesComeFromTheArchiveForTheNamedParameter() {
        final var battery = databases.of(SATELLITE).orElseThrow().parameter("BATTERY_VOLTAGE").orElseThrow();
        final var expected = new ParameterValuesResponse("BATTERY_VOLTAGE", "V", List.of(), null);
        given(archive.values(SATELLITE, battery, DEFAULTS)).willReturn(expected);

        assertThat(service.values(SATELLITE, "BATTERY_VOLTAGE", DEFAULTS)).isEqualTo(expected);
    }

    @Test
    void satelliteWithoutAMissionDatabaseIsNotFound() {
        assertThatThrownBy(() -> service.parameters(new NoradId(25544)))
                .isInstanceOf(NoMissionDatabaseException.class)
                .hasMessage("No mission database for satellite 25544");
    }

    @Test
    void unknownParameterIsNotFound() {
        assertThatThrownBy(() -> service.values(SATELLITE, "SOLAR_CURRENT", DEFAULTS))
                .isInstanceOf(UnknownParameterException.class)
                .hasMessage("No parameter SOLAR_CURRENT in the mission database of satellite 99999");
    }
}
