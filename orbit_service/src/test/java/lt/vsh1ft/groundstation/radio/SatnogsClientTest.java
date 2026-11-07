package lt.vsh1ft.groundstation.radio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;

import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SatnogsClientTest {

    private static final String URL = "https://db.satnogs.org/api/transmitters/?format=json";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://db.satnogs.org");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final SatnogsClient client = new SatnogsClient(builder.build());

    @Test
    @Requirement("IF-020")
    void transmittersAreReadFromTheSatnogsJson() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                [{"uuid": "ZJxCeQmih9zDfYNVrB4wRN", "description": "Mode V APRS", "alive": true,
                  "type": "Transceiver", "uplink_low": 145825000, "uplink_high": null, "uplink_drift": null,
                  "downlink_low": 145825000, "downlink_high": null, "downlink_drift": null, "mode": "AFSK",
                  "mode_id": 49, "uplink_mode": "AFSK", "invert": false, "baud": 1200.0,
                  "sat_id": "XSKZ-5603-1870-9019-3066", "norad_cat_id": 25544, "norad_follow_id": null,
                  "status": "active", "updated": "2019-06-16T08:49:33.586159Z",
                  "citation": "https://spaceflight.nasa.gov/station/reference/radio/", "service": "Unknown"}]
                """, MediaType.APPLICATION_JSON));

        assertThat(client.transmitters()).containsExactly(new SatnogsTransmitter("ZJxCeQmih9zDfYNVrB4wRN", 25544,
                "Mode V APRS", "Transceiver", "active", 145825000L, null, 145825000L, null, "AFSK", "AFSK", 1200.0,
                false, Instant.parse("2019-06-16T08:49:33.586159Z")));
    }

    @Test
    void emptyResponseMeansNoTransmitters() {
        server.expect(requestTo(URL)).andRespond(withSuccess());

        assertThat(client.transmitters()).isEmpty();
    }
}
