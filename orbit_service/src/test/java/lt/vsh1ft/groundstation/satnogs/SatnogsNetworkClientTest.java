package lt.vsh1ft.groundstation.satnogs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SatnogsNetworkClientTest {

    private static final NoradId FRONTIERSAT = new NoradId(69015);
    private static final Instant START = Instant.parse("2026-11-08T17:30:00Z");
    private static final Instant END = Instant.parse("2026-11-08T19:00:00Z");
    private static final String FIRST = "https://network.satnogs.org/api/observations/?norad_cat_id=69015"
            + "&start=2026-11-08T17%3A30%3A00Z&end=2026-11-08T19%3A00%3A00Z&format=json";
    private static final String NEXT = "https://network.satnogs.org/api/observations/?cursor=cD0yMDI2&format=json"
            + "&norad_cat_id=69015";
    private static final String FRAME = "https://s3.eu-central-1.wasabisys.com/satnogs-network/data_obs/2026/11/8/18/"
            + "15386644/data_15386644_2026-11-08T19-03-34_3";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://network.satnogs.org");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final SatnogsNetworkClient client = new SatnogsNetworkClient(builder.build());

    @Test
    @Requirement("IF-080")
    void observationsAreReadWithTheirStationAndFramesAndTheCursorToTheNextPage() {
        final var headers = new HttpHeaders();
        headers.add(HttpHeaders.LINK, "<" + NEXT + ">; rel=\"next\", <https://x/prev>; rel=\"prev\"");
        server.expect(requestTo(FIRST)).andRespond(withSuccess("""
                [{"id": 15386644, "start": "2026-11-08T18:58:28Z", "end": "2026-11-08T19:08:59Z",
                  "ground_station": 3020, "norad_cat_id": 69015, "station_name": "Dtec Station",
                  "station_lat": 25.12, "station_lng": 55.377, "station_alt": 32, "status": "good",
                  "demoddata": [{"payload_demod": "%s"}]}]
                """.formatted(FRAME), MediaType.APPLICATION_JSON).headers(headers));

        final var page = client.observations(FRONTIERSAT, START, END, null);

        assertThat(page.next()).isEqualTo(NEXT);
        assertThat(page.observations()).containsExactly(new SatnogsObservation(15386644,
                Instant.parse("2026-11-08T18:58:28Z"), Instant.parse("2026-11-08T19:08:59Z"), "Dtec Station", 25.12,
                55.377, 32.0, List.of(new SatnogsObservation.Demoddata(FRAME))));
    }

    @Test
    @Requirement("IF-080")
    void nextPageIsFetchedFromTheCursorAndTheLastPageHasNoNext() {
        server.expect(requestTo(NEXT)).andRespond(withSuccess("""
                [{"id": 1, "start": "2026-11-07T10:00:00Z", "end": "2026-11-07T10:10:00Z", "station_name": "A",
                  "station_lat": null, "station_lng": null, "station_alt": null, "demoddata": null}]
                """, MediaType.APPLICATION_JSON));

        final var page = client.observations(FRONTIERSAT, START, END, NEXT);

        assertThat(page.next()).isNull();
        assertThat(page.observations()).singleElement()
                .satisfies(observation -> assertThat(observation.demoddata()).isEmpty());
    }

    @Test
    void emptyBodyMeansNoObservations() {
        server.expect(requestTo(FIRST)).andRespond(withSuccess());

        assertThat(client.observations(FRONTIERSAT, START, END, null).observations()).isEmpty();
    }

    @Test
    @Requirement("IF-080")
    void frameFileIsDownloadedFromItsOwnAddress() {
        server.expect(requestTo(FRAME)).andRespond(withSuccess(new byte[] {(byte) 0xC2, 0x21},
                MediaType.APPLICATION_OCTET_STREAM));

        assertThat(client.download(FRAME)).containsExactly(0xC2, 0x21);
    }

    @Test
    void emptyFrameFileIsAnEmptyFrame() {
        server.expect(requestTo(FRAME)).andRespond(withSuccess());

        assertThat(client.download(FRAME)).isEmpty();
    }

    @Test
    @Requirement("SAT-010")
    void throttledRequestSaysHowLongSatnogsAsksToWait() {
        server.expect(requestTo(FIRST)).andRespond(withTooManyRequests().headers(retryAfter("1048")));

        assertThatThrownBy(() -> client.observations(FRONTIERSAT, START, END, null))
                .isInstanceOfSatisfying(SatnogsThrottledException.class,
                        throttled -> assertThat(throttled.retryAfter()).contains(Duration.ofSeconds(1048)));
    }

    @Test
    @Requirement("SAT-010")
    void throttledRequestWithoutAUsableWaitTimeGivesNone() {
        server.expect(requestTo(FIRST)).andRespond(withTooManyRequests().headers(retryAfter("soon")));
        server.expect(requestTo(NEXT)).andRespond(withTooManyRequests());

        assertThatThrownBy(() -> client.observations(FRONTIERSAT, START, END, null))
                .isInstanceOfSatisfying(SatnogsThrottledException.class,
                        throttled -> assertThat(throttled.retryAfter()).isEmpty());
        assertThatThrownBy(() -> client.observations(FRONTIERSAT, START, END, NEXT))
                .isInstanceOfSatisfying(SatnogsThrottledException.class,
                        throttled -> assertThat(throttled.retryAfter()).isEmpty());
    }

    private static HttpHeaders retryAfter(String value) {
        final var headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, value);
        return headers;
    }

    @Test
    void requestsNameTheProject() {
        assertThat(SatnogsNetworkConfig.USER_AGENT).startsWith("ground-segment-api");
    }
}
