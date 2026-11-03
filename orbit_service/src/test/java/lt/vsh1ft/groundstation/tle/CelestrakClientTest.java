package lt.vsh1ft.groundstation.tle;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import lt.vsh1ft.groundstation.pass.NoradId;
import lt.vsh1ft.groundstation.pass.SatelliteNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CelestrakClientTest {

    private static final NoradId UNKNOWN = new NoradId(99999);
    private static final String URL = "https://celestrak.org/NORAD/elements/gp.php?CATNR=99999&FORMAT=JSON";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://celestrak.org");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final CelestrakClient client = new CelestrakClient(builder.build());

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
    void serverErrorIsAFailureNotAnUnknownSatellite() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.latest(UNKNOWN)).isInstanceOf(CelestrakException.class);
    }
}
