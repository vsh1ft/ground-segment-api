package lt.vsh1ft.groundstation.station;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import lt.vsh1ft.groundstation.PostgresTestcontainer;
import lt.vsh1ft.groundstation.pass.GroundStation;
import lt.vsh1ft.groundstation.pass.GroundStationSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@ApplicationModuleTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class StationModuleTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private GroundStationSource stations;

    @Test
    void stationCreatedOverHttpIsAvailableToPassPrediction() {
        final var created = mvc.post().uri("/stations").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Vilnius", "latitudeDeg": 54.67519, "longitudeDeg": 25.23422, "altitudeM": 114,
                         "minElevationDeg": 5}
                        """)
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        final var location = created.getResponse().getHeader("Location");
        final var id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        assertThat(stations.byId(id))
                .isEqualTo(new GroundStation("Vilnius", 54.67519, 25.23422, 114.0, 5.0));
    }
}
