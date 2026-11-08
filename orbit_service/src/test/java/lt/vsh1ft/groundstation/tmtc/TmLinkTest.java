package lt.vsh1ft.groundstation.tmtc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import lt.vsh1ft.ccsds.TmPacket;
import lt.vsh1ft.groundstation.contact.ContactWatch;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TmLinkTest {

    static final byte[] REFERENCE = HexFormat.of()
            .parseHex("0864C00500122003190001000012345678800001020304FB2C");
    private static final NoradId SATELLITE = new NoradId(99999);
    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private static final int WAIT_MS = 5_000;

    private final TelemetryReception reception = mock(TelemetryReception.class);
    private final ContactWatch contacts = mock(ContactWatch.class);
    private ServerSocket simulator;
    private TmLink link;

    @BeforeEach
    void openSimulatorPort() throws IOException {
        simulator = new ServerSocket(0);
    }

    @AfterEach
    void stopLinkAndSimulator() throws IOException {
        if (link.isRunning()) {
            link.stop();
        }
        simulator.close();
    }

    @Test
    @Requirement({"TM-010", "IF-040"})
    void receivedPacketsAreArchivedForTheLinksSatelliteWithTheirReceptionTime() throws IOException {
        link = start(simulator.getLocalPort());

        try (var connection = simulator.accept()) {
            connection.getOutputStream().write(concat(REFERENCE, REFERENCE));

            verify(reception, timeout(WAIT_MS).times(2)).receive(SATELLITE, TmPacket.decode(REFERENCE), NOW);
        }
        verify(contacts).watch(SATELLITE, "Vilnius");
    }

    @Test
    @Requirement("TM-020")
    void linkReconnectsAfterTheSimulatorClosedTheConnection() throws IOException {
        link = start(simulator.getLocalPort());

        try (var first = simulator.accept()) {
            first.getOutputStream().write(REFERENCE);
        }
        try (var second = simulator.accept()) {
            second.getOutputStream().write(REFERENCE);

            verify(reception, timeout(WAIT_MS).times(2)).receive(SATELLITE, TmPacket.decode(REFERENCE), NOW);
        }
    }

    @Test
    @Requirement("TM-020")
    void linkConnectsOnceTheSimulatorComesUp() throws IOException, InterruptedException {
        final var port = simulator.getLocalPort();
        simulator.close();
        link = start(port);
        Thread.sleep(500);

        simulator = new ServerSocket(port);
        try (var connection = simulator.accept()) {
            connection.getOutputStream().write(REFERENCE);

            verify(reception, timeout(WAIT_MS)).receive(SATELLITE, TmPacket.decode(REFERENCE), NOW);
        }
    }

    @Test
    @Requirement("TM-030")
    void corruptedPacketIsNotArchivedAndTheLinkStartsOverOnANewConnection() throws IOException {
        final var corrupted = REFERENCE.clone();
        corrupted[19] = 0x00;
        link = start(simulator.getLocalPort());

        try (var first = simulator.accept()) {
            first.getOutputStream().write(concat(corrupted, REFERENCE));
        }
        try (var second = simulator.accept()) {
            second.getOutputStream().write(REFERENCE);

            verify(reception, timeout(WAIT_MS)).receive(SATELLITE, TmPacket.decode(REFERENCE), NOW);
        }
        verify(reception, times(1)).receive(any(), any(), any());
    }

    @Test
    void stoppedLinkLetsGoOfItsConnection() throws IOException {
        link = start(simulator.getLocalPort());

        try (var connection = simulator.accept()) {
            link.stop();

            assertThat(link.isRunning()).isFalse();
            assertThat(connection.getInputStream().read()).isEqualTo(-1);
        }
    }

    private TmLink start(int port) {
        final var properties = new TmLinkProperties(SATELLITE.value(), "Vilnius", "localhost", port,
                Duration.ofMillis(200), Duration.ofMillis(50), null);
        final var started = new TmLink(properties, reception, contacts, Clock.fixed(NOW, ZoneOffset.UTC));
        started.start();
        return started;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        return ByteBuffer.allocate(first.length + second.length).put(first).put(second).array();
    }
}
