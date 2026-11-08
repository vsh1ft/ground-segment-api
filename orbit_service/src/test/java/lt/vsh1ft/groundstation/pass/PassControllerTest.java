package lt.vsh1ft.groundstation.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lt.vsh1ft.groundstation.catalog.SatelliteNotInCatalogException;
import lt.vsh1ft.groundstation.catalog.SatelliteService;
import lt.vsh1ft.groundstation.elements.OrbitDataUnavailableException;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.station.StationNotFoundException;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(PassController.class)
class PassControllerTest {

    private static final NoradId ISS = new NoradId(25544);
    private static final Instant AOS = Instant.parse("2026-11-02T19:56:46Z");
    private static final Instant ELEMENTS_EPOCH = Instant.parse("2026-11-01T18:56:25.712736Z");
    private static final UUID VILNIUS_ID = UUID.fromString("3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42");
    private static final GroundStation VILNIUS = new GroundStation("Vilnius", 54.67519, 25.23422, 114.0, 5.0);
    private static final String ISS_OVER_VILNIUS = "/passes?satellite=25544&station=" + VILNIUS_ID;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PassForecastService service;

    @MockitoBean
    private GroundStationSource stations;

    @MockitoBean
    private SatelliteService satellites;

    @BeforeEach
    void knownStation() {
        given(stations.byId(VILNIUS_ID)).willReturn(VILNIUS);
    }

    @Test
    @Requirement("PASS-040")
    void passesAreReturnedInUtcWithTheDirectionOfRisePeakAndSet() {
        final var pass = new Pass(new Pass.Crossing(AOS, 338.2149), new Pass.Crossing(AOS.plusSeconds(172), 102.4671),
                new Pass.Peak(AOS.plusSeconds(86), 6.2, 40.1234));
        given(service.forecast(ISS, VILNIUS, Duration.ofHours(12))).willReturn(forecastOf(pass));

        final var response = mvc.get().uri(ISS_OVER_VILNIUS + "&duration=PT12H").exchange();

        assertThat(response).hasStatusOk().bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "satellite": 25544,
                          "station": "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42",
                          "elementsEpoch": "2026-11-01T18:56:25.712Z",
                          "passes": [{
                            "start": "2026-11-02T19:56:46Z", "startAzimuthDeg": 338.21, "startDirection": "NNW",
                            "peak": {"time": "2026-11-02T19:58:12Z", "elevationDeg": 6.2, "azimuthDeg": 40.12,
                                     "direction": "NE"},
                            "end": "2026-11-02T19:59:38Z", "endAzimuthDeg": 102.47, "endDirection": "ESE"
                          }]
                        }
                        """);
    }

    @Test
    @Requirement("PASS-040")
    void timesAreTruncatedToMilliseconds() {
        final var aos = Instant.parse("2026-11-03T20:43:05.676060570Z");
        final var pass = new Pass(new Pass.Crossing(aos, 300.0), new Pass.Crossing(aos.plusSeconds(300), 100.0),
                new Pass.Peak(aos.plusSeconds(150), 18.6, 200.0));
        given(service.forecast(eq(ISS), any(), any())).willReturn(forecastOf(pass));

        assertThat(mvc.get().uri(ISS_OVER_VILNIUS)).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("""
                        {"passes": [{"start": "2026-11-03T20:43:05.676Z"}]}
                        """);
    }

    @Test
    @Requirement("PASS-040")
    void anglesAreRoundedToHundredthsOfADegreeAndJustBelowNorthBecomesNorth() {
        final var pass = new Pass(new Pass.Crossing(AOS, 359.996), new Pass.Crossing(AOS.plusSeconds(300), 0.004),
                new Pass.Peak(AOS.plusSeconds(150), 43.08517791061351, 11.2649));
        given(service.forecast(eq(ISS), any(), any())).willReturn(forecastOf(pass));

        assertThat(mvc.get().uri(ISS_OVER_VILNIUS)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"passes": [{"startAzimuthDeg": 0.0, "startDirection": "N",
                             "peak": {"elevationDeg": 43.09, "azimuthDeg": 11.26, "direction": "NNE"},
                             "endAzimuthDeg": 0.0, "endDirection": "N"}]}
                """);
    }

    @Test
    @Requirement("PASS-010")
    void durationDefaultsToOneDay() {
        given(service.forecast(eq(ISS), any(), eq(Duration.ofDays(1)))).willReturn(forecastOf());

        assertThat(mvc.get().uri(ISS_OVER_VILNIUS)).hasStatusOk()
                .bodyJson().extractingPath("$.passes").asArray().isEmpty();
    }

    @Test
    @Requirement("API-010")
    void missingSatelliteIsABadRequest() {
        assertBadRequestProblem("/passes?station=" + VILNIUS_ID, """
                [{"field": "satellite", "message": "must not be null"}]
                """);
    }

    @Test
    @Requirement("API-010")
    void missingStationIsABadRequest() {
        assertBadRequestProblem("/passes?satellite=25544", """
                [{"field": "station", "message": "must not be null"}]
                """);
    }

    @Test
    @Requirement("API-010")
    void satelliteThatIsNotANumberIsABadRequest() {
        assertBadRequestProblem("/passes?satellite=abc&station=" + VILNIUS_ID, """
                [{"field": "satellite", "message": "invalid value"}]
                """);
    }

    @Test
    @Requirement("API-010")
    void stationThatIsNotAUuidIsABadRequest() {
        assertBadRequestProblem("/passes?satellite=25544&station=vilnius", """
                [{"field": "station", "message": "invalid value"}]
                """);
    }

    @Test
    @Requirement("API-030")
    void zeroSatelliteIsABadRequest() {
        assertBadRequestProblem("/passes?satellite=0&station=" + VILNIUS_ID, """
                [{"field": "satellite", "message": "must be greater than 0"}]
                """);
    }

    @Test
    @Requirement("PASS-010")
    void durationLongerThanSevenDaysIsABadRequest() {
        assertBadRequestProblem(ISS_OVER_VILNIUS + "&duration=PT169H", """
                [{"field": "duration", "message": "must be shorter than or equal to 7 days"}]
                """);
    }

    @Test
    @Requirement("API-010")
    void zeroDurationIsABadRequest() {
        assertBadRequestProblem(ISS_OVER_VILNIUS + "&duration=PT0S", """
                [{"field": "duration", "message": "must be longer than 0"}]
                """);
    }

    @Test
    @Requirement("API-010")
    void negativeDurationIsABadRequest() {
        assertBadRequestProblem(ISS_OVER_VILNIUS + "&duration=-PT1H", """
                [{"field": "duration", "message": "must be longer than 0"}]
                """);
    }

    @Test
    @Requirement("API-010")
    void everyInvalidParameterIsReported() {
        assertBadRequestProblem("/passes?satellite=0&duration=P8D", """
                [{"field": "duration", "message": "must be shorter than or equal to 7 days"}, {"field": "satellite", "message": "must be greater than 0"}, {"field": "station", "message": "must not be null"}]
                """);
    }

    @Test
    @Requirement("PASS-050")
    void unknownStationIsNotFound() {
        final var unknown = UUID.fromString("00000000-0000-0000-0000-000000000001");
        given(stations.byId(unknown)).willThrow(new StationNotFoundException(unknown));

        assertThat(mvc.get().uri("/passes?satellite=25544&station=" + unknown)).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("No station with ID " + unknown);
        verifyNoInteractions(service);
    }

    @Test
    @Requirement("PASS-050")
    void satelliteOutsideTheCatalogIsNotFound() {
        final var unknown = new NoradId(99999);
        willThrow(new SatelliteNotInCatalogException(unknown)).given(satellites).ensureInCatalog(unknown);

        assertThat(mvc.get().uri("/passes?satellite=99999&station=" + VILNIUS_ID)).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("No satellite with NORAD ID 99999 in the catalog");
        verifyNoInteractions(service);
    }

    @Test
    @Requirement("PASS-050")
    void satelliteWithoutStoredElementsIsServiceUnavailable() {
        given(service.forecast(eq(ISS), any(), any()))
                .willThrow(new OrbitDataUnavailableException("No orbit data stored for NORAD ID 25544"));

        assertThat(mvc.get().uri(ISS_OVER_VILNIUS)).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .bodyJson().extractingPath("$.detail").isEqualTo("No orbit data stored for NORAD ID 25544");
    }

    private static PassForecast forecastOf(Pass... passes) {
        return new PassForecast(ELEMENTS_EPOCH, List.of(passes));
    }

    private void assertBadRequestProblem(String uri, String errors) {
        final var response = mvc.get().uri(uri).exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response).bodyJson().isLenientlyEqualTo("{\"errors\": " + errors + "}");
        verifyNoInteractions(service);
    }
}
