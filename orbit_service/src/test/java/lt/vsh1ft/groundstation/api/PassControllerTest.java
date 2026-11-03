package lt.vsh1ft.groundstation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import lt.vsh1ft.groundstation.GroundStationProperties;
import lt.vsh1ft.groundstation.pass.NoradId;
import lt.vsh1ft.groundstation.pass.OrbitDataUnavailableException;
import lt.vsh1ft.groundstation.pass.Pass;
import lt.vsh1ft.groundstation.pass.PassForecastService;
import lt.vsh1ft.groundstation.pass.SatelliteNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(PassController.class)
@EnableConfigurationProperties(GroundStationProperties.class)
class PassControllerTest {

    private static final NoradId ISS = new NoradId(25544);
    private static final Instant AOS = Instant.parse("2026-11-02T19:56:46Z");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PassForecastService service;

    @Test
    void passesAreReturnedInUtc() {
        final var pass = new Pass(AOS, AOS.plusSeconds(172), new Pass.Peak(AOS.plusSeconds(86), 6.2));
        given(service.forecast(eq(ISS), any(), eq(Duration.ofHours(12)))).willReturn(List.of(pass));

        final var response = mvc.get().uri("/passes?satellite=25544&duration=PT12H").exchange();

        assertThat(response).hasStatusOk().bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "satellite": 25544,
                          "station": "Vilnius",
                          "passes": [{
                            "start": "2026-11-02T19:56:46Z",
                            "peak": {"time": "2026-11-02T19:58:12Z", "elevationDeg": 6.2},
                            "end": "2026-11-02T19:59:38Z"
                          }]
                        }
                        """);
    }

    @Test
    void timesAreTruncatedToMilliseconds() {
        final var aos = Instant.parse("2026-11-03T20:43:05.676060570Z");
        final var pass = new Pass(aos, aos.plusSeconds(300), new Pass.Peak(aos.plusSeconds(150), 18.6));
        given(service.forecast(eq(ISS), any(), any())).willReturn(List.of(pass));

        assertThat(mvc.get().uri("/passes?satellite=25544")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("""
                        {"passes": [{"start": "2026-11-03T20:43:05.676Z"}]}
                        """);
    }

    @Test
    void elevationIsRoundedToHundredthsOfADegree() {
        final var pass = new Pass(AOS, AOS.plusSeconds(300), new Pass.Peak(AOS.plusSeconds(150), 43.08517791061351));
        given(service.forecast(eq(ISS), any(), any())).willReturn(List.of(pass));

        assertThat(mvc.get().uri("/passes?satellite=25544")).hasStatusOk()
                .bodyJson().extractingPath("$.passes[0].peak.elevationDeg").isEqualTo(43.09);
    }

    @Test
    void durationDefaultsToOneDay() {
        given(service.forecast(eq(ISS), any(), eq(Duration.ofDays(1)))).willReturn(List.of());

        assertThat(mvc.get().uri("/passes?satellite=25544")).hasStatusOk()
                .bodyJson().extractingPath("$.passes").asArray().isEmpty();
    }

    @Test
    void missingSatelliteIsABadRequest() {
        assertBadRequestProblem("/passes", "Required parameter 'satellite' is not present.");
    }

    @Test
    void satelliteThatIsNotANumberIsABadRequest() {
        assertBadRequestProblem("/passes?satellite=abc", "Failed to convert 'satellite' with value: 'abc'");
    }

    @Test
    void zeroSatelliteIsABadRequest() {
        assertBadRequestProblem("/passes?satellite=0", "satellite: must be greater than 0");
    }

    @Test
    void durationLongerThanSevenDaysIsABadRequest() {
        assertBadRequestProblem("/passes?satellite=25544&duration=PT169H",
                "duration: must be shorter than or equal to 7 days");
    }

    @Test
    void zeroDurationIsABadRequest() {
        assertBadRequestProblem("/passes?satellite=25544&duration=PT0S", "duration: must be longer than 0");
    }

    @Test
    void negativeDurationIsABadRequest() {
        assertBadRequestProblem("/passes?satellite=25544&duration=-PT1H", "duration: must be longer than 0");
    }

    @Test
    void everyInvalidParameterIsReported() {
        assertBadRequestProblem("/passes?satellite=0&duration=P8D",
                "satellite: must be greater than 0; duration: must be shorter than or equal to 7 days");
    }

    private void assertBadRequestProblem(String uri, String detail) {
        final var response = mvc.get().uri(uri).exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response).bodyJson().extractingPath("$.detail").isEqualTo(detail);
        verifyNoInteractions(service);
    }

    @Test
    void satelliteWithoutOrbitDataIsNotFound() {
        given(service.forecast(eq(new NoradId(99999)), any(), any()))
                .willThrow(new SatelliteNotFoundException(new NoradId(99999), null));

        assertThat(mvc.get().uri("/passes?satellite=99999")).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("No orbit data for NORAD ID 99999");
    }

    @Test
    void satelliteWithoutStoredDataWhileCelestrakIsDownIsServiceUnavailable() {
        given(service.forecast(eq(ISS), any(), any()))
                .willThrow(new OrbitDataUnavailableException("CelesTrak is unavailable", null));

        assertThat(mvc.get().uri("/passes?satellite=25544")).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .bodyJson().extractingPath("$.detail").isEqualTo("CelesTrak is unavailable");
    }
}
