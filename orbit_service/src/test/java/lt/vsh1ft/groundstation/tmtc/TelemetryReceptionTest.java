package lt.vsh1ft.groundstation.tmtc;

import static lt.vsh1ft.groundstation.tmtc.SimulatorTelemetry.LIVE;
import static lt.vsh1ft.groundstation.tmtc.SimulatorTelemetry.LIVE_GENERATED;
import static lt.vsh1ft.groundstation.tmtc.SimulatorTelemetry.SATELLITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lt.vsh1ft.ccsds.MessageType;
import lt.vsh1ft.ccsds.TmPacket;
import lt.vsh1ft.groundstation.contact.ContactWatch;
import lt.vsh1ft.groundstation.contact.Visibility;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import lt.vsh1ft.xtce.EnumeratedValue;
import lt.vsh1ft.xtce.ParameterValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class TelemetryReceptionTest {

    private static final Instant RECEIVED = Instant.parse("2026-11-08T08:53:42Z");
    private static final Instant GENERATED = Instant.parse("2026-11-08T09:00:00Z");

    private final TmPacketArchive packets = mock(TmPacketArchive.class);
    private final ParameterArchive parameters = mock(ParameterArchive.class);
    private final EventArchive events = mock(EventArchive.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final ContactWatch contacts = mock(ContactWatch.class);
    private final TelemetryReception reception = new TelemetryReception(packets, parameters, events,
            SimulatorTelemetry.databases(), publisher, contacts);

    @BeforeEach
    void everyPacketIsReceivedInAPass() {
        given(contacts.received(any(), any())).willReturn(Visibility.IN_PASS);
    }

    @Test
    @Requirement("CON-020")
    void packetIsStoredWithTheVisibilityOfItsReceptionTime() {
        given(contacts.received(SATELLITE, RECEIVED)).willReturn(Visibility.OUTSIDE_PASS);

        reception.receive(SATELLITE, LIVE, RECEIVED);

        verify(packets).store(SATELLITE, LIVE, RECEIVED, Visibility.OUTSIDE_PASS);
        verify(parameters).store(eq(SATELLITE), anyLong(), eq(LIVE_GENERATED), any());
    }

    @Test
    @Requirement("PAR-020")
    void housekeepingPacketIsArchivedAndItsParameterValuesStoredWithTheGenerationTime() {
        given(packets.store(SATELLITE, LIVE, RECEIVED, Visibility.IN_PASS)).willReturn(12L);

        reception.receive(SATELLITE, LIVE, RECEIVED);

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<ParameterValue>> values = ArgumentCaptor.forClass(List.class);
        verify(parameters).store(eq(SATELLITE), eq(12L), eq(LIVE_GENERATED), values.capture());
        assertThat(values.getValue()).hasSize(19).last().satisfies(mode -> {
            assertThat(mode.parameter().name()).isEqualTo("SPACECRAFT_MODE");
            assertThat(mode.engineering()).isEqualTo(Optional.of(new EnumeratedValue("NOMINAL")));
        });
        verifyNoInteractions(events);
        verify(publisher).publishEvent(new ParameterValuesReceived(SATELLITE, 12L, LIVE_GENERATED,
                values.getValue()));
    }

    @Test
    @Requirement("PAR-030")
    void packetOfAnUnknownHousekeepingStructureIsArchivedWithoutValues() {
        final var structure2 = SimulatorTelemetry.housekeeping(1, LIVE_GENERATED, "021CF3009E01");

        reception.receive(SATELLITE, structure2, RECEIVED);

        verify(packets).store(SATELLITE, structure2, RECEIVED, Visibility.IN_PASS);
        verifyNoInteractions(parameters, publisher);
    }

    @Test
    @Requirement("PAR-030")
    void packetTooShortForItsLayoutIsArchivedWithoutValuesAndTheCrcIsNotReadAsData() {
        final TmPacket shortStructure1 = SimulatorTelemetry.housekeeping(1, LIVE_GENERATED, "011CF3009E");

        reception.receive(SATELLITE, shortStructure1, RECEIVED);

        verify(packets).store(SATELLITE, shortStructure1, RECEIVED, Visibility.IN_PASS);
        verifyNoInteractions(parameters);
    }

    @Test
    void packetOfASatelliteWithoutAMissionDatabaseIsOnlyArchived() {
        final var other = new NoradId(25544);

        reception.receive(other, LIVE, RECEIVED);

        verify(packets).store(other, LIVE, RECEIVED, Visibility.IN_PASS);
        verify(parameters, never()).store(any(), anyLong(), any(), any());
    }

    @Test
    @Requirement({"EVT-010", "EVT-020"})
    void eventReportIsLoggedWithItsNameDescriptionAndContainerAndItsValuesStored() {
        final var batteryLow = SimulatorTelemetry.packet(MessageType.LOW_SEVERITY_ANOMALY_REPORT, 812, GENERATED,
                "00011BED");
        given(packets.store(SATELLITE, batteryLow, RECEIVED, Visibility.IN_PASS)).willReturn(40L);

        reception.receive(SATELLITE, batteryLow, RECEIVED);

        verify(events).store(new StoredEvent(null, SATELLITE.value(), 40L, Severity.LOW, 1, "BATTERY_LOW",
                "Battery voltage below 7.15 V", "EVENT_BATTERY_LOW", GENERATED));
        verify(parameters).store(eq(SATELLITE), eq(40L), eq(GENERATED), any());
    }

    @Test
    @Requirement("EVT-010")
    void eventWithAnIdTheDatabaseDoesNotKnowIsLoggedWithoutAName() {
        final var unknown = SimulatorTelemetry.packet(MessageType.HIGH_SEVERITY_ANOMALY_REPORT, 1, GENERATED, "0063");
        given(packets.store(SATELLITE, unknown, RECEIVED, Visibility.IN_PASS)).willReturn(41L);

        reception.receive(SATELLITE, unknown, RECEIVED);

        verify(events).store(new StoredEvent(null, SATELLITE.value(), 41L, Severity.HIGH, 99, null, null, null,
                GENERATED));
        verifyNoInteractions(parameters);
    }

    @Test
    @Requirement("EVT-010")
    void eventThatCannotBeDecodedIsStillLoggedWithItsSeverity() {
        final var empty = SimulatorTelemetry.packet(MessageType.MEDIUM_SEVERITY_ANOMALY_REPORT, 1, GENERATED, "");
        final var other = new NoradId(25544);
        given(packets.store(SATELLITE, empty, RECEIVED, Visibility.IN_PASS)).willReturn(42L);
        given(packets.store(other, empty, RECEIVED, Visibility.IN_PASS)).willReturn(43L);

        reception.receive(SATELLITE, empty, RECEIVED);
        reception.receive(other, empty, RECEIVED);

        verify(events).store(new StoredEvent(null, SATELLITE.value(), 42L, Severity.MEDIUM, null, null, null, null,
                GENERATED));
        verify(events).store(new StoredEvent(null, 25544, 43L, Severity.MEDIUM, null, null, null, null, GENERATED));
    }
}
