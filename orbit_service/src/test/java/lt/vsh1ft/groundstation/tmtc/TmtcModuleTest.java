package lt.vsh1ft.groundstation.tmtc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import lt.vsh1ft.ccsds.Apid;
import lt.vsh1ft.ccsds.MessageType;
import lt.vsh1ft.ccsds.MissionTime;
import lt.vsh1ft.ccsds.PusTmSecondaryHeader;
import lt.vsh1ft.ccsds.TmPacket;
import lt.vsh1ft.groundstation.PostgresTestcontainer;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;

@ApplicationModuleTest
@Import(PostgresTestcontainer.class)
@TestPropertySource(properties = "groundstation.tmtc.link.host=tm-link.invalid")
class TmtcModuleTest {

    private static final NoradId SATELLITE = new NoradId(99999);
    private static final NoradId OTHER_SATELLITE = new NoradId(99998);
    private static final Instant T0 = Instant.parse("2026-11-08T10:00:00Z");
    private static final MessageType EVENT_REPORT = new MessageType(5, 1);

    @Autowired
    private TmPacketArchive archive;

    @Autowired
    private JdbcAggregateOperations aggregates;

    @Autowired
    private TelemetryReception reception;

    @Autowired
    private ParameterService parameters;

    @Autowired
    private EventService events;

    @BeforeEach
    void emptyArchive() {
        aggregates.deleteAll(StoredTmPacket.class);
    }

    @Test
    @Requirement({"TM-010", "TM-040"})
    void packetsOfOneSatelliteArePagedNewestFirstWithACursor() {
        archive.store(SATELLITE, packet(0, 100, MessageType.HOUSEKEEPING_PARAMETER_REPORT, T0), T0);
        archive.store(OTHER_SATELLITE, packet(1, 100, MessageType.HOUSEKEEPING_PARAMETER_REPORT, T0), T0);
        archive.store(SATELLITE, packet(2, 100, MessageType.HOUSEKEEPING_PARAMETER_REPORT, T0), T0);
        archive.store(SATELLITE, packet(3, 100, MessageType.HOUSEKEEPING_PARAMETER_REPORT, T0), T0);

        final var first = archive.packets(SATELLITE, query(2, null));
        final var second = archive.packets(SATELLITE, query(2, first.nextBefore()));

        assertThat(first.packets()).extracting(TmPacketResponse::sequenceCount).containsExactly(3, 2);
        assertThat(second.packets()).extracting(TmPacketResponse::sequenceCount).containsExactly(0);
        assertThat(second.nextBefore()).isNull();
        assertThat(second.packets().getFirst().raw())
                .isEqualTo(hex(packet(0, 100, MessageType.HOUSEKEEPING_PARAMETER_REPORT, T0)));
    }

    @Test
    @Requirement("TM-040")
    void lastFullPageHasNoCursor() {
        archive.store(SATELLITE, packet(0, 100, MessageType.HOUSEKEEPING_PARAMETER_REPORT, T0), T0);
        archive.store(SATELLITE, packet(1, 100, MessageType.HOUSEKEEPING_PARAMETER_REPORT, T0), T0);

        assertThat(archive.packets(SATELLITE, query(2, null)).nextBefore()).isNull();
    }

    @Test
    @Requirement("TM-050")
    void packetsAreFilteredByGenerationTimeFromInclusiveToExclusive() {
        for (int second = 0; second < 4; second++) {
            archive.store(SATELLITE, packet(second, 100, MessageType.HOUSEKEEPING_PARAMETER_REPORT,
                    T0.plusSeconds(second)), T0);
        }

        final var found = archive.packets(SATELLITE,
                new PacketsQuery(null, null, T0.plusSeconds(1), T0.plusSeconds(3), null, null, null));

        assertThat(found.packets()).extracting(TmPacketResponse::sequenceCount).containsExactly(2, 1);
    }

    @Test
    @Requirement("TM-050")
    void packetsAreFilteredByApidServiceAndSubtype() {
        archive.store(SATELLITE, packet(0, 100, MessageType.HOUSEKEEPING_PARAMETER_REPORT, T0), T0);
        archive.store(SATELLITE, packet(1, 101, MessageType.HOUSEKEEPING_PARAMETER_REPORT, T0), T0);
        archive.store(SATELLITE, packet(2, 100, EVENT_REPORT, T0), T0);
        archive.store(SATELLITE, packet(3, 100, new MessageType(5, 2), T0), T0);
        archive.store(SATELLITE, packet(4, 100, new MessageType(3, 26), T0), T0);

        final var found = archive.packets(SATELLITE, new PacketsQuery(null, null, null, null, 100, 5, 1));

        assertThat(found.packets()).extracting(TmPacketResponse::sequenceCount).containsExactly(2);
    }

    @Test
    @Requirement({"PAR-020", "PAR-050"})
    void receivedHousekeepingIsQueryableAsParameterValues() {
        reception.receive(SATELLITE, SimulatorTelemetry.LIVE, T0);

        final var battery = parameters.values(SATELLITE, "BATTERY_VOLTAGE", values(20, null));
        final var mode = parameters.values(SATELLITE, "SPACECRAFT_MODE", values(20, null));
        final var apid = parameters.values(SATELLITE, "APID", values(20, null));

        assertThat(battery.unit()).isEqualTo("V");
        assertThat(battery.values()).singleElement().satisfies(value -> {
            assertThat(value.raw()).isEqualTo(7411);
            assertThat((double) value.engineering()).isCloseTo(7.411, within(1e-9));
            assertThat(value.generationTime()).isCloseTo(SimulatorTelemetry.LIVE_GENERATED,
                    within(1, ChronoUnit.MILLIS));
            assertThat(value.packetId()).isPositive();
        });
        assertThat(mode.values().getFirst().engineering()).isEqualTo("NOMINAL");
        assertThat(apid.values().getFirst().engineering()).isEqualTo(100L);
    }

    @Test
    @Requirement("PAR-050")
    void parameterValuesArePagedNewestFirstAndFilteredByGenerationTime() {
        for (int second = 0; second < 4; second++) {
            reception.receive(SATELLITE, SimulatorTelemetry.housekeeping(second, T0.plusSeconds(second),
                    "011CF%d009E01".formatted(second)), T0);
        }
        reception.receive(OTHER_SATELLITE, SimulatorTelemetry.LIVE, T0);

        final var first = parameters.values(SATELLITE, "BATTERY_VOLTAGE", values(2, null));
        final var second = parameters.values(SATELLITE, "BATTERY_VOLTAGE", values(2, first.nextBefore()));
        final var window = parameters.values(SATELLITE, "BATTERY_VOLTAGE",
                new ValuesQuery(null, null, T0.plusSeconds(1), T0.plusSeconds(3)));

        assertThat(first.values()).extracting(ParameterValuesResponse.Value::raw).containsExactly(0x1CF3L, 0x1CF2L);
        assertThat(second.values()).extracting(ParameterValuesResponse.Value::raw).containsExactly(0x1CF1L, 0x1CF0L);
        assertThat(second.nextBefore()).isNull();
        assertThat(window.values()).extracting(ParameterValuesResponse.Value::raw).containsExactly(0x1CF2L, 0x1CF1L);
    }

    @Test
    @Requirement({"EVT-010", "EVT-020", "EVT-030"})
    void eventLogListsTheSpacecraftsEventsWithTheirValuesNewestFirst() {
        reception.receive(SATELLITE, SimulatorTelemetry.packet(MessageType.LOW_SEVERITY_ANOMALY_REPORT, 1,
                T0, "00011BED"), T0);
        reception.receive(SATELLITE, SimulatorTelemetry.packet(MessageType.HIGH_SEVERITY_ANOMALY_REPORT, 2,
                T0, "0005"), T0);
        reception.receive(SATELLITE, SimulatorTelemetry.housekeeping(3, T0, "011BED009E00"), T0);
        reception.receive(SATELLITE, SimulatorTelemetry.packet(MessageType.INFORMATIVE_EVENT_REPORT, 4,
                T0.plusSeconds(60), "00021C52"), T0);
        reception.receive(OTHER_SATELLITE, SimulatorTelemetry.packet(MessageType.HIGH_SEVERITY_ANOMALY_REPORT, 5,
                T0, "0005"), T0);

        final var all = events.events(SATELLITE, new EventsQuery(null, null, null, null, null));
        final var serious = events.events(SATELLITE, new EventsQuery(null, null, null, null, Severity.LOW));
        final var page = events.events(SATELLITE, new EventsQuery(1, null, null, T0.plusSeconds(60), null));
        final var next = events.events(SATELLITE, new EventsQuery(1, page.nextBefore(), null, null, null));
        final var later = events.events(SATELLITE, new EventsQuery(null, null, T0.plusSeconds(60), null, null));

        assertThat(all.events()).extracting(EventsResponse.Event::name)
                .containsExactly("BATTERY_RECOVERED", "SAFE_MODE_ENTERED", "BATTERY_LOW");
        assertThat(all.events().getLast().values()).singleElement().satisfies(voltage -> {
            assertThat(voltage.parameter()).isEqualTo("EVENT_BATTERY_VOLTAGE");
            assertThat(voltage.raw()).isEqualTo(7149);
            assertThat((double) voltage.engineering()).isCloseTo(7.149, within(1e-9));
        });
        assertThat(serious.events()).extracting(EventsResponse.Event::severity)
                .containsExactly(Severity.HIGH, Severity.LOW);
        assertThat(page.events()).extracting(EventsResponse.Event::name).containsExactly("SAFE_MODE_ENTERED");
        assertThat(next.events()).extracting(EventsResponse.Event::name).containsExactly("BATTERY_LOW");
        assertThat(later.events()).extracting(EventsResponse.Event::name).containsExactly("BATTERY_RECOVERED");
    }

    private static ValuesQuery values(int limit, Long before) {
        return new ValuesQuery(limit, before, null, null);
    }

    private static PacketsQuery query(int limit, Long before) {
        return new PacketsQuery(limit, before, null, null, null, null, null);
    }

    private static TmPacket packet(int sequenceCount, int apid, MessageType type, Instant generated) {
        final var header = new PusTmSecondaryHeader(0, type, 0, 0, MissionTime.toCuc(generated));
        return new TmPacket(new Apid(apid), sequenceCount, header, new byte[] {1});
    }

    private static String hex(TmPacket packet) {
        return HexFormat.of().withUpperCase().formatHex(packet.encode());
    }
}
