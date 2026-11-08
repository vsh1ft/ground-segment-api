package lt.vsh1ft.groundstation.alarm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;

import lt.vsh1ft.groundstation.ClockConfig;
import lt.vsh1ft.groundstation.PostgresTestcontainer;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.tmtc.ParameterValuesReceived;
import lt.vsh1ft.groundstation.traceability.Requirement;
import lt.vsh1ft.xtce.MissionDatabase;
import lt.vsh1ft.xtce.MonitoringLevel;
import lt.vsh1ft.xtce.ParameterValue;
import lt.vsh1ft.xtce.XtceReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

@ApplicationModuleTest
@Import({PostgresTestcontainer.class, ClockConfig.class})
class AlarmModuleTest {

    private static final NoradId SATELLITE = new NoradId(99999);
    private static final NoradId OTHER_SATELLITE = new NoradId(99998);
    private static final Instant T0 = Instant.parse("2026-11-08T09:00:00Z");
    private static final MissionDatabase SIMULATOR = simulator();

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AlarmService alarms;

    @MockitoSpyBean
    private AlarmArchive archive;

    @BeforeEach
    void noAlarms() {
        jdbc.update("DELETE FROM alarm");
    }

    @Test
    @Requirement({"ALM-020", "ALM-030", "ALM-040", "ALM-050"})
    void batteryDipRaisesEscalatesReturnsAndClearsOneAlarm() {
        receive(SATELLITE, 0, 7205, 300);
        receive(SATELLITE, 1, 7199, 321);
        receive(OTHER_SATELLITE, 1, 7000, 300);
        receive(SATELLITE, 160, 7119, 300);
        receive(SATELLITE, 270, 7200, 300);

        final var open = alarms.open(SATELLITE).alarms();
        assertThat(open).extracting(AlarmResponse::parameter).containsExactly("PLATFORM_TEMPERATURE",
                "BATTERY_VOLTAGE");
        final var battery = open.getLast();
        assertThat(battery.level()).isEqualTo(MonitoringLevel.CRITICAL);
        assertThat(battery.state()).isEqualTo(AlarmState.RETURNED);
        assertThat(battery.triggeredAt()).isEqualTo(T0.plusSeconds(1));
        assertThat(battery.triggerValue()).isEqualTo(7.199);
        assertThat(battery.returnedAt()).isEqualTo(T0.plusSeconds(270));
        assertThat(open.getFirst().state()).isEqualTo(AlarmState.RETURNED);
        assertThat(open.getFirst().level()).isEqualTo(MonitoringLevel.WARNING);
        assertThat(alarms.open(OTHER_SATELLITE).alarms()).singleElement()
                .satisfies(alarm -> assertThat(alarm.level()).isEqualTo(MonitoringLevel.CRITICAL));

        assertThat(alarms.acknowledge(SATELLITE, battery.id()).state()).isEqualTo(AlarmState.CLEARED);
        receive(SATELLITE, 400, 7190, 300);

        assertThat(alarms.open(SATELLITE).alarms()).extracting(AlarmResponse::parameter, AlarmResponse::state)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("BATTERY_VOLTAGE", AlarmState.TRIGGERED),
                        org.assertj.core.groups.Tuple.tuple("PLATFORM_TEMPERATURE", AlarmState.RETURNED));
    }

    @Test
    @Requirement("ALM-060")
    void failedAlarmUpdateRollsBackOnlyItselfAndTheTelemetryTransactionCommits() {
        doAnswer(invocation -> jdbc.queryForObject("SELECT 1 / 0", Integer.class)).when(archive).save(any());

        transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO alarm (version, norad_id, parameter, level, state, triggered_at, trigger_value) "
                    + "VALUES (0, 99999, 'STORED_BEFORE', 'WARNING', 'CLEARED', now(), 0)");
            publisher.publishEvent(received(SATELLITE, 1, 7000, 300));
        });

        assertThat(jdbc.queryForList("SELECT parameter FROM alarm", String.class)).containsExactly("STORED_BEFORE");
    }

    private void receive(NoradId satellite, int second, int batteryMillivolts, int temperatureDeciCelsius) {
        transaction.executeWithoutResult(status -> publisher.publishEvent(received(satellite, second,
                batteryMillivolts, temperatureDeciCelsius)));
    }

    private static ParameterValuesReceived received(NoradId satellite, int second, int batteryMillivolts,
                                                    int temperatureDeciCelsius) {
        return new ParameterValuesReceived(satellite, second, T0.plusSeconds(second), List.of(
                value("BATTERY_VOLTAGE", batteryMillivolts),
                value("PLATFORM_TEMPERATURE", temperatureDeciCelsius),
                value("SPACECRAFT_MODE", 0)));
    }

    private static ParameterValue value(String name, long raw) {
        final var parameter = SIMULATOR.parameter(name).orElseThrow();
        return new ParameterValue(parameter, raw, parameter.type().calibrate(raw));
    }

    private static MissionDatabase simulator() {
        try (InputStream xtce = new ClassPathResource("mdb/simulator.xml").getInputStream()) {
            return XtceReader.read(xtce);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
