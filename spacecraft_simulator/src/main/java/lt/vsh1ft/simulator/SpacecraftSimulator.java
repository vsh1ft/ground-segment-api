package lt.vsh1ft.simulator;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class SpacecraftSimulator {

    private static final System.Logger LOG = System.getLogger(SpacecraftSimulator.class.getName());

    private SpacecraftSimulator() {
    }

    public static void main(String[] args) throws IOException {
        final var config = SimulatorConfig.from(System.getenv());
        final var view = GroundContact.from(System.getenv()).map(StationView::load);
        final var orbitPeriod = view.map(StationView::orbitPeriod).orElse(config.orbitPeriod());
        final Predicate<Instant> inView = view.<Predicate<Instant>>map(station -> station::inView)
                .orElse(_ -> true);
        final var clock = Clock.systemUTC();
        final var server = new TelemetryServer(config.port());
        final var downlink = new Downlink(new TelemetryGenerator(new Spacecraft(clock.instant(), orbitPeriod)),
                inView, new LinkImpairment(config.frameDropRate(), config.frameCorruptRate(), new Random()),
                server::broadcast);
        server.start();

        final var scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> downlink.step(clock.instant()),
                0, config.housekeepingPeriod().toMillis(), TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(downlink::frameSlot, 0, Downlink.FRAME_PERIOD.toMillis(), TimeUnit.MILLISECONDS);
        LOG.log(System.Logger.Level.INFO, "Sending housekeeping every {0} in a frame every {1} on TCP port {2}; "
                        + "orbit period {3}; {4}; dropping {5} and corrupting {6} of the frames",
                config.housekeepingPeriod(), Downlink.FRAME_PERIOD, String.valueOf(server.port()), orbitPeriod,
                view.isPresent() ? "transmitting only in view of the station" : "transmitting all the time",
                config.frameDropRate(), config.frameCorruptRate());

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
