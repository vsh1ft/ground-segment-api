package lt.vsh1ft.groundstation.elements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import lt.vsh1ft.groundstation.shared.NoradId;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CelestrakClientTest {

    private static final NoradId UNKNOWN = new NoradId(99999);
    private static final String URL = "https://celestrak.org/NORAD/elements/gp.php?CATNR=99999&FORMAT=JSON";
    private static final String GROUP_URL = "https://celestrak.org/NORAD/elements/gp.php?GROUP=amateur&FORMAT=JSON";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://celestrak.org");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final CelestrakClient client = new CelestrakClient(builder.build());

    @Test
    void latestElementsOfASatelliteAreReturned() {
        server.expect(requestTo("https://celestrak.org/NORAD/elements/gp.php?CATNR=25544&FORMAT=JSON"))
                .andRespond(withSuccess("[" + omm("ISS (ZARYA)", "1998-067A", 25544) + "]", MediaType.APPLICATION_JSON));

        assertThat(client.latest(new NoradId(25544))).extracting(StoredOmm::objectName).isEqualTo("ISS (ZARYA)");
    }

    @Test
    void notFoundMeansTheSatelliteIsUnknown() {
        server.expect(requestTo(URL)).andRespond(
                withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN).body("No GP data found"));

        assertThatThrownBy(() -> client.latest(UNKNOWN)).isInstanceOf(SatelliteNotFoundException.class);
    }

    @Test
    void emptyListMeansTheSatelliteIsUnknown() {
        server.expect(requestTo(URL)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.latest(UNKNOWN)).isInstanceOf(SatelliteNotFoundException.class);
    }

    @Test
    void emptyResponseMeansTheSatelliteIsUnknown() {
        server.expect(requestTo(URL)).andRespond(withSuccess());

        assertThatThrownBy(() -> client.latest(UNKNOWN)).isInstanceOf(SatelliteNotFoundException.class);
    }

    @Test
    void groupIsDownloadedInOneRequest() {
        server.expect(requestTo(GROUP_URL)).andRespond(withSuccess(
                "[" + omm("ISS (ZARYA)", "1998-067A", 25544) + "," + omm("NOAA 19", "2009-005A", 33591) + "]",
                MediaType.APPLICATION_JSON));

        assertThat(client.group("amateur")).extracting(StoredOmm::noradId).containsExactly(25544, 33591);
    }

    @Test
    void groupServerErrorIsAFailure() {
        server.expect(requestTo(GROUP_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.group("amateur")).isInstanceOf(CelestrakException.class)
                .hasMessageStartingWith("CelesTrak request for group amateur failed");
    }

    @Test
    void serverErrorIsAFailureNotAnUnknownSatellite() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.latest(UNKNOWN)).isInstanceOf(CelestrakException.class);
    }

    private static String omm(String name, String designator, int noradId) {
        return """
                {"OBJECT_NAME": "%s", "OBJECT_ID": "%s", "EPOCH": "2026-11-01T18:56:25.712736",
                 "MEAN_MOTION": 15.49143202, "ECCENTRICITY": 0.0007095, "INCLINATION": 51.6332,
                 "RA_OF_ASC_NODE": 339.1679, "ARG_OF_PERICENTER": 336.8847, "MEAN_ANOMALY": 23.1822,
                 "EPHEMERIS_TYPE": 0, "CLASSIFICATION_TYPE": "U", "NORAD_CAT_ID": %d, "ELEMENT_SET_NO": 999,
                 "REV_AT_EPOCH": 59308, "BSTAR": 0.00011503, "MEAN_MOTION_DOT": 5.925e-5, "MEAN_MOTION_DDOT": 0}
                """.formatted(name, designator, noradId);
    }
}
