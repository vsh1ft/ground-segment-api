package lt.vsh1ft.groundstation.tmtc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.ccsds.FrameSynchronizer;
import lt.vsh1ft.groundstation.contact.ContactWatch;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class TmLink implements SmartLifecycle {

    private final TmLinkProperties properties;
    private final TelemetryReception reception;
    private final ContactWatch contacts;
    private final Clock clock;

    private NoradId satellite;
    private volatile boolean running;
    private ExecutorService receiver;
    private boolean downReported;

    @Override
    public void start() {
        satellite = properties.satellite();
        contacts.watch(satellite, properties.station());
        running = true;
        receiver = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("tm-link").factory());
        receiver.execute(this::receiveUntilStopped);
    }

    @Override
    public void stop() {
        running = false;
        receiver.shutdownNow();
        receiver.close();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void receiveUntilStopped() {
        while (running) {
            receiveOneConnection();
            pause();
        }
    }

    private void receiveOneConnection() {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(properties.host(), properties.port()),
                    (int) properties.connectTimeout().toMillis());
            log.info("TM link connected to {}:{}", properties.host(), properties.port());
            downReported = false;
            final var frames = new FrameDemultiplexer(satellite, properties.spacecraftId(), reception, contacts);
            final var synchronizer = new FrameSynchronizer(socket.getInputStream(), properties.frameLength());
            for (var frame = synchronizer.next(); frame.isPresent(); frame = synchronizer.next()) {
                frames.receive(frame.get(), clock.instant());
            }
            reportDown("closed by the simulator");
        } catch (IOException | RuntimeException e) {
            reportDown(e.toString());
        }
    }

    private void reportDown(String reason) {
        if (!running) {
            return;
        }
        if (downReported) {
            log.debug("TM link still down: {}", reason);
            return;
        }
        log.warn("TM link to {}:{} down: {}; reconnecting every {}", properties.host(), properties.port(), reason,
                properties.reconnectDelay());
        downReported = true;
    }

    private void pause() {
        try {
            Thread.sleep(properties.reconnectDelay());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
