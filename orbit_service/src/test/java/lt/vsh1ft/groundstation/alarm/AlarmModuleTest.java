package lt.vsh1ft.groundstation.alarm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;

import lt.vsh1ft.groundstation.ClockConfig;
import lt.vsh1ft.groundstation.PostgresTestcontainer;
import lt.vsh1ft.groundstation.contact.LinkDegraded;
import lt.vsh1ft.groundstation.contact.LinkRecovered;
import lt.vsh1ft.groundstation.contact.SilenceEnded;
import lt.vsh1ft.groundstation.contact.SilenceStarted;
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
import org.springframework.dao.DuplicateKeyException;
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
                .containsExactly(tuple("BATTERY_VOLTAGE", AlarmState.TRIGGERED),
                        tuple("PLATFORM_TEMPERATURE", AlarmState.RETURNED));
    }

    @Test
    @Requirement("ALM-060")
    void failedAlarmUpdateRollsBackOnlyItselfAndTheTelemetryTransactionCommits() {
        doAnswer(invocation -> jdbc.queryForObject("SELECT 1 / 0", Integer.class)).when(archive).save(any());

        transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO alarm (version, norad_id, kind, parameter, level, state, triggered_at, "
                    + "trigger_value) VALUES (0, 99999, 'LIMIT', 'STORED_BEFORE', 'WARNING', 'CLEARED', now(), 0)");
            publisher.publishEvent(received(SATELLITE, 1, 7000, 300));
        });

        assertThat(jdbc.queryForList("SELECT parameter FROM alarm", String.class)).containsExactly("STORED_BEFORE");
    }

    @Test
    @Requirement("ALM-070")
    void silenceRaisesOneLossOfSignalAlarmThatReturnsAndIsRaisedAgain() {
        receive(SATELLITE, 1, 7199, 300);
        publisher.publishEvent(new SilenceStarted(SATELLITE, T0.plusSeconds(20)));
        publisher.publishEvent(new SilenceStarted(SATELLITE, T0.plusSeconds(25)));
        publisher.publishEvent(new SilenceEnded(SATELLITE, T0.plusSeconds(40)));
        publisher.publishEvent(new SilenceEnded(SATELLITE, T0.plusSeconds(41)));

        final var lost = lossOfSignal(SATELLITE);
        assertThat(lost.level()).isEqualTo(MonitoringLevel.WARNING);
        assertThat(lost.state()).isEqualTo(AlarmState.RETURNED);
        assertThat(lost.triggeredAt()).isEqualTo(T0.plusSeconds(20));
        assertThat(lost.returnedAt()).isEqualTo(T0.plusSeconds(40));
        assertThat(lost.parameter()).isNull();
        assertThat(lost.triggerValue()).isNull();

        publisher.publishEvent(new SilenceStarted(SATELLITE, T0.plusSeconds(5700)));

        assertThat(lossOfSignal(SATELLITE).id()).isEqualTo(lost.id());
        assertThat(lossOfSignal(SATELLITE).state()).isEqualTo(AlarmState.TRIGGERED);
        assertThat(alarms.open(SATELLITE).alarms()).extracting(AlarmResponse::kind)
                .containsExactlyInAnyOrder(AlarmKind.LOSS_OF_SIGNAL, AlarmKind.LIMIT);
    }

    @Test
    @Requirement("ALM-070")
    void acknowledgedLossOfSignalClearsWhenTelemetryReturnsAndANewSilenceRaisesANewAlarm() {
        publisher.publishEvent(new SilenceStarted(SATELLITE, T0));
        final var lost = lossOfSignal(SATELLITE);
        alarms.acknowledge(SATELLITE, lost.id());

        publisher.publishEvent(new SilenceEnded(SATELLITE, T0.plusSeconds(30)));
        publisher.publishEvent(new SilenceStarted(SATELLITE, T0.plusSeconds(5700)));

        assertThat(lossOfSignal(SATELLITE).id()).isNotEqualTo(lost.id());
        assertThat(alarms.open(OTHER_SATELLITE).alarms()).isEmpty();
    }

    @Test
    @Requirement("ALM-070")
    void databaseAllowsOneOpenLossOfSignalAlarmPerSatellite() {
        insertLossOfSignal("TRIGGERED");
        insertLossOfSignal("CLEARED");

        assertThatThrownBy(() -> insertLossOfSignal("RETURNED")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @Requirement("ALM-070")
    void failedLossOfSignalUpdateIsLoggedAndNotThrown() {
        doAnswer(invocation -> jdbc.queryForObject("SELECT 1 / 0", Integer.class)).when(archive).save(any());
        publisher.publishEvent(new SilenceStarted(SATELLITE, T0));
        doAnswer(invocation -> jdbc.queryForObject("SELECT 1 / 0", Integer.class)).when(archive).open(any(), any());

        publisher.publishEvent(new SilenceEnded(SATELLITE, T0.plusSeconds(30)));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM alarm", Integer.class)).isZero();
    }

    @Test
    @Requirement("ALM-080")
    void degradedLinkRaisesALinkQualityAlarmBesideALossOfSignal() {
        publisher.publishEvent(new LinkDegraded(SATELLITE, T0.plusSeconds(30)));
        publisher.publishEvent(new SilenceStarted(SATELLITE, T0.plusSeconds(40)));
        publisher.publishEvent(new LinkRecovered(SATELLITE, T0.plusSeconds(60)));

        assertThat(alarms.open(SATELLITE).alarms())
                .extracting(AlarmResponse::kind, AlarmResponse::state, AlarmResponse::returnedAt)
                .containsExactly(tuple(AlarmKind.LOSS_OF_SIGNAL, AlarmState.TRIGGERED, null),
                        tuple(AlarmKind.LINK_QUALITY, AlarmState.RETURNED, T0.plusSeconds(60)));

        publisher.publishEvent(new LinkDegraded(SATELLITE, T0.plusSeconds(5700)));

        assertThat(alarms.open(SATELLITE).alarms()).filteredOn(alarm -> alarm.kind() == AlarmKind.LINK_QUALITY)
                .singleElement().satisfies(alarm -> assertThat(alarm.state()).isEqualTo(AlarmState.TRIGGERED));
    }

    @Test
    @Requirement("ALM-080")
    void failedLinkQualityUpdateIsLoggedAndNotThrown() {
        doAnswer(invocation -> jdbc.queryForObject("SELECT 1 / 0", Integer.class)).when(archive).open(any(), any());

        publisher.publishEvent(new LinkDegraded(SATELLITE, T0));
        publisher.publishEvent(new LinkRecovered(SATELLITE, T0.plusSeconds(30)));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM alarm", Integer.class)).isZero();
    }

    private AlarmResponse lossOfSignal(NoradId satellite) {
        return alarms.open(satellite).alarms().stream()
                .filter(alarm -> alarm.kind() == AlarmKind.LOSS_OF_SIGNAL)
                .findFirst().orElseThrow();
    }

    private void insertLossOfSignal(String state) {
        jdbc.update("INSERT INTO alarm (version, norad_id, kind, level, state, triggered_at) "
                + "VALUES (0, 99999, 'LOSS_OF_SIGNAL', 'WARNING', ?, now())", state);
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
