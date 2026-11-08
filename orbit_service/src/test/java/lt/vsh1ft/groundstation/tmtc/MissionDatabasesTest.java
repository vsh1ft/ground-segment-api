package lt.vsh1ft.groundstation.tmtc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import lt.vsh1ft.xtce.AlarmRange;
import lt.vsh1ft.xtce.AlarmRanges;
import lt.vsh1ft.xtce.FloatParameterType;
import lt.vsh1ft.xtce.InvalidMissionDatabaseException;
import lt.vsh1ft.xtce.MonitoringLevel;
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

    @Test
    @Requirement("ALM-010")
    void simulatorLimitsAreLoadedForHousekeepingButNotForEventValues() {
        final var database = SimulatorTelemetry.databases().of(SimulatorTelemetry.SATELLITE).orElseThrow();
        final var battery = (FloatParameterType) database.parameter("BATTERY_VOLTAGE").orElseThrow().type();
        final var temperature = (FloatParameterType) database.parameter("PLATFORM_TEMPERATURE").orElseThrow().type();
        final var eventVoltage = (FloatParameterType) database.parameter("EVENT_BATTERY_VOLTAGE").orElseThrow().type();

        assertThat(battery.limits()).isEqualTo(new AlarmRanges(new AlarmRange(7.20, true, 7.80, true),
                new AlarmRange(7.12, true, 7.90, true)));
        assertThat(temperature.limits().check(32.0)).isEqualTo(MonitoringLevel.IN_LIMITS);
        assertThat(temperature.limits().check(32.1)).isEqualTo(MonitoringLevel.WARNING);
        assertThat(temperature.limits().check(34.6)).isEqualTo(MonitoringLevel.CRITICAL);
        assertThat(temperature.limits().check(-273.0)).isEqualTo(MonitoringLevel.IN_LIMITS);
        assertThat(eventVoltage.limits()).isEqualTo(AlarmRanges.NONE);
    }

    @Test
    @Requirement("ALM-010")
    void inconsistentLimitsStopTheStartup() {
        final var xtce = """
                <SpaceSystem xmlns="http://www.omg.org/spec/XTCE/20180204" name="BAD">
                  <TelemetryMetaData><ParameterTypeSet>
                    <FloatParameterType name="v_t"><IntegerDataEncoding/><DefaultAlarm><StaticAlarmRanges>
                      <WarningRange maxInclusive="8.0"/><CriticalRange maxInclusive="7.9"/>
                    </StaticAlarmRanges></DefaultAlarm></FloatParameterType>
                  </ParameterTypeSet></TelemetryMetaData>
                </SpaceSystem>""";
        final var link = SimulatorTelemetry.link(new ByteArrayResource(xtce.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> new MissionDatabases(link)).isInstanceOf(InvalidMissionDatabaseException.class)
                .hasMessage("StaticAlarmRanges in DefaultAlarm in FloatParameterType 'v_t' is inconsistent: the "
                        + "warning maximum 8.0 lies outside the critical maximum 7.9");
    }
}
