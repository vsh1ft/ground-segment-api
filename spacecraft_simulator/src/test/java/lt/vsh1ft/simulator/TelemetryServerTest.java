package lt.vsh1ft.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lt.vsh1ft.ccsds.TmPacket;
import lt.vsh1ft.ccsds.TmPacketReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TelemetryServerTest {

    private static final Instant START = Instant.parse("2026-11-08T00:00:00Z");

    private final TmPacket packet = new TelemetryGenerator(new Spacecraft(START, Duration.ofMinutes(92))).step(START)
            .getFirst();
    private final ScheduledExecutorService sender = Executors.newSingleThreadScheduledExecutor();
    private TelemetryServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new TelemetryServer(0);
        server.start();
        sender.scheduleAtFixedRate(() -> server.broadcast(packet.encode()), 0, 20, TimeUnit.MILLISECONDS);
    }

    @AfterEach
    void stopServer() throws IOException {
        sender.shutdownNow();
        server.close();
    }

    @Test
    void connectedClientReceivesTheBroadcastPackets() throws IOException {
        try (var client = connect()) {
            assertThat(new TmPacketReader(client.getInputStream()).next()).contains(packet);
        }
    }

    @Test
    void clientThatWentAwayDoesNotStopTheOthersFromReceiving() throws IOException, InterruptedException {
        connect().close();
        Thread.sleep(200);

        try (var client = connect()) {
            assertThat(new TmPacketReader(client.getInputStream()).next()).contains(packet);
        }
    }

    private Socket connect() throws IOException {
        final var socket = new Socket("localhost", server.port());
        socket.setSoTimeout(5_000);
        return socket;
    }
}
