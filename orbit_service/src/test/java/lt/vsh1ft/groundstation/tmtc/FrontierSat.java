package lt.vsh1ft.groundstation.tmtc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.core.io.ClassPathResource;

final class FrontierSat {

    static final NoradId SATELLITE = new NoradId(69015);

    private FrontierSat() {
    }

    static byte[] beacon() {
        return HexFormat.of().parseHex(text("satnogs/frontiersat-beacon.hex").strip());
    }

    static Map<String, Long> expectedRawValues() {
        final var values = new LinkedHashMap<String, Long>();
        text("satnogs/frontiersat-beacon-raw.csv").lines()
                .filter(line -> !line.startsWith("#"))
                .map(line -> line.split(","))
                .forEach(field -> values.put(field[0], Long.parseLong(field[1])));
        return values;
    }

    static MissionDatabases databases() {
        return new MissionDatabases(SimulatorTelemetry.link(new ClassPathResource("mdb/simulator.xml")),
                new TmtcProperties(List.of(new TmtcProperties.SatelliteDatabase(SATELLITE.value(),
                        new ClassPathResource("mdb/frontiersat.xml"), FrameCheck.CRC32C_TRAILER))));
    }

    private static String text(String resource) {
        try {
            return new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
