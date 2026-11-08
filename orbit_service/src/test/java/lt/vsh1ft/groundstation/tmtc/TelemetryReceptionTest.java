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

import lt.vsh1ft.ccsds.TmPacket;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import lt.vsh1ft.xtce.EnumeratedValue;
import lt.vsh1ft.xtce.ParameterValue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TelemetryReceptionTest {

    private static final Instant RECEIVED = Instant.parse("2026-11-08T08:53:42Z");

    private final TmPacketArchive packets = mock(TmPacketArchive.class);
    private final ParameterArchive parameters = mock(ParameterArchive.class);
    private final TelemetryReception reception = new TelemetryReception(packets, parameters,
            SimulatorTelemetry.databases());

    @Test
    @Requirement("PAR-020")
    void housekeepingPacketIsArchivedAndItsParameterValuesStoredWithTheGenerationTime() {
        given(packets.store(SATELLITE, LIVE, RECEIVED)).willReturn(12L);

        reception.receive(SATELLITE, LIVE, RECEIVED);

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<ParameterValue>> values = ArgumentCaptor.forClass(List.class);
        verify(parameters).store(eq(SATELLITE), eq(12L), eq(LIVE_GENERATED), values.capture());
        assertThat(values.getValue()).hasSize(19).last().satisfies(mode -> {
            assertThat(mode.parameter().name()).isEqualTo("SPACECRAFT_MODE");
            assertThat(mode.engineering()).isEqualTo(Optional.of(new EnumeratedValue("NOMINAL")));
        });
    }

    @Test
    @Requirement("PAR-030")
    void packetOfAnUnknownHousekeepingStructureIsArchivedWithoutValues() {
        final var structure2 = SimulatorTelemetry.housekeeping(1, LIVE_GENERATED, "021CF3009E01");

        reception.receive(SATELLITE, structure2, RECEIVED);

        verify(packets).store(SATELLITE, structure2, RECEIVED);
        verifyNoInteractions(parameters);
    }

    @Test
    @Requirement("PAR-030")
    void packetTooShortForItsLayoutIsArchivedWithoutValuesAndTheCrcIsNotReadAsData() {
        final TmPacket shortStructure1 = SimulatorTelemetry.housekeeping(1, LIVE_GENERATED, "011CF3009E");

        reception.receive(SATELLITE, shortStructure1, RECEIVED);

        verify(packets).store(SATELLITE, shortStructure1, RECEIVED);
        verifyNoInteractions(parameters);
    }

    @Test
    void packetOfASatelliteWithoutAMissionDatabaseIsOnlyArchived() {
        final var other = new NoradId(25544);

        reception.receive(other, LIVE, RECEIVED);

        verify(packets).store(other, LIVE, RECEIVED);
        verify(parameters, never()).store(any(), anyLong(), any(), any());
    }
}
