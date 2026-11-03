package lt.vsh1ft.groundstation.tle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import lt.vsh1ft.groundstation.orekit.OrekitDataLoader;
import lt.vsh1ft.groundstation.pass.NoradId;
import lt.vsh1ft.groundstation.pass.SatelliteNotFoundException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@Tag("live")
class CelestrakClientLiveTest {

    private static final NoradId ISS = new NoradId(25544);

    private final CelestrakClient client =
            new CelestrakClient(RestClient.builder().baseUrl("https://celestrak.org").build());

    @BeforeAll
    static void loadOrekitData() {
        new OrekitDataLoader(new File("orekit-data"));
    }

    @Test
    void issOmmIsAvailableRecentAndUsable() {
        final var omm = client.latest(ISS);

        assertThat(omm.noradId()).isEqualTo(25544);
        assertThat(omm.objectId()).isEqualTo("1998-067A");
        assertThat(omm.epoch()).isAfter(LocalDateTime.now(ZoneOffset.UTC).minus(Duration.ofDays(7)));
        assertThat(OmmTleConverter.toTle(omm).getSatelliteNumber()).isEqualTo(25544);
    }

    @Test
    void unknownSatelliteIsReportedAsUnknown() {
        assertThatThrownBy(() -> client.latest(new NoradId(99999)))
                .isInstanceOf(SatelliteNotFoundException.class);
    }
}
