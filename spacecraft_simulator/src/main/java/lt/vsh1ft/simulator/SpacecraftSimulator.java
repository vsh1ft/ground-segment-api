package lt.vsh1ft.simulator;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class SpacecraftSimulator {

    private static final System.Logger LOG = System.getLogger(SpacecraftSimulator.class.getName());

    private SpacecraftSimulator() {
    }

    public static void main(String[] args) throws IOException {
        final var config = SimulatorConfig.from(System.getenv());
        final var clock = Clock.systemUTC();
        final var generator = new TelemetryGenerator(new Spacecraft(clock.instant()));
        final var server = new TelemetryServer(config.port());
        server.start();

        final var scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> server.broadcast(generator.housekeeping(clock.instant()).encode()),
                0, config.housekeepingPeriod().toMillis(), TimeUnit.MILLISECONDS);
        LOG.log(System.Logger.Level.INFO, "Sending housekeeping every {0} on TCP port {1}",
                config.housekeepingPeriod(), String.valueOf(server.port()));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdownNow();
            try {
                server.close();
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "Closing the server failed: {0}", e.getMessage());
            }
        }));
    }
}
