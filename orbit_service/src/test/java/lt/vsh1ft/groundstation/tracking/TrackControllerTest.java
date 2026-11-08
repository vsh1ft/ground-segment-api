package lt.vsh1ft.groundstation.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lt.vsh1ft.groundstation.catalog.SatelliteNotInCatalogException;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(TrackController.class)
class TrackControllerTest {

    private static final NoradId ISS = new NoradId(25544);
    private static final UUID STATION = UUID.fromString("3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42");
    private static final String URI = "/satellites/25544/track?station=" + STATION;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TrackService service;

    @Test
    @Requirement({"TRK-010", "TRK-020"})
    void trackListsTheFrequenciesOnceAndACorrectedValuePerPoint() {
        final var query = new TrackQuery(STATION, Instant.parse("2026-11-01T00:41:17Z"), Duration.ofMinutes(9),
                Duration.ofSeconds(1), null, null);
        given(service.track(ISS, query)).willReturn(new TrackResponse(25544, STATION,
                Instant.parse("2026-10-31T04:14:04Z"), Duration.ofSeconds(1),
                List.of(new Frequency("ZJxCeQmih9zDfYNVrB4wRN", "Mode V APRS", Direction.DOWNLINK, 145825000)),
                List.of(new TrackPoint(Instant.parse("2026-11-01T00:41:17Z"), 262.057, 5.024, 1876.78, -6.762,
                        List.of(145828289L)))));

        assertThat(mvc.get().uri(URI + "&from=2026-11-01T00:41:17Z&duration=PT9M")).hasStatusOk().bodyJson()
                .isStrictlyEqualTo("""
                        {"satellite": 25544, "station": "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42",
                         "elementsEpoch": "2026-10-31T04:14:04Z", "step": "PT1S",
                         "frequencies": [{"transmitter": "ZJxCeQmih9zDfYNVrB4wRN", "description": "Mode V APRS",
                                          "direction": "DOWNLINK", "nominalHz": 145825000}],
                         "points": [{"time": "2026-11-01T00:41:17Z", "azimuthDeg": 262.057, "elevationDeg": 5.024,
                                     "rangeKm": 1876.78, "rangeRateKmPerS": -6.762, "frequenciesHz": [145828289]}]}
                        """);
    }

    @Test
    @Requirement("TRK-040")
    void defaultsAreFifteenMinutesAtOneSecond() {
        given(service.track(ISS, new TrackQuery(STATION, null, Duration.ofMinutes(15), Duration.ofSeconds(1), null,
                null))).willReturn(new TrackResponse(25544, STATION, Instant.EPOCH, Duration.ofSeconds(1), List.of(),
                List.of()));

        assertThat(mvc.get().uri(URI)).hasStatusOk().bodyJson().extractingPath("$.step").isEqualTo("PT1S");
    }

    @ParameterizedTest
    @ValueSource(strings = {"&step=PT0.1S", "&step=PT1H", "&duration=P1D&step=PT10S", "&duration=PT0.000000001S"})
    @Requirement("TRK-040")
    void everyLimitIsAccepted(String parameters) {
        given(service.track(any(), any())).willReturn(new TrackResponse(25544, STATION, Instant.EPOCH,
                Duration.ofSeconds(1), List.of(), List.of()));

        assertThat(mvc.get().uri(URI + parameters)).hasStatusOk();
    }

    @ParameterizedTest
    @ValueSource(strings = {"&step=PT0.099S", "&step=PT1H0.001S", "&duration=PT24H0.000000001S", "&duration=PT0S",
            "&downlinkHz=0", "&uplinkHz=-1"})
    @Requirement({"TRK-040", "API-010"})
    void valuesBeyondTheLimitsAreABadRequest(String parameters) {
        assertThat(mvc.get().uri(URI + parameters)).hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        verifyNoInteractions(service);
    }

    @Test
    @Requirement("API-010")
    void missingStationIsABadRequest() {
        assertThat(mvc.get().uri("/satellites/25544/track")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .isLenientlyEqualTo("{\"errors\": [{\"field\": \"station\", \"message\": \"must not be null\"}]}");
        verifyNoInteractions(service);
    }

    @Test
    @Requirement("TRK-040")
    void trackWithTooManyPointsIsABadRequestThatSaysWhy() {
        given(service.track(any(), any())).willThrow(new TooManyPointsException(Duration.ofDays(1),
                Duration.ofSeconds(1), 86_401, 10_000));

        assertThat(mvc.get().uri(URI + "&duration=P1D")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .isLenientlyEqualTo("""
                        {"detail": "A track of PT24H at a step of PT1S has 86401 points; at most 10000 are allowed, \
                        so ask for a shorter track or a longer step"}
                        """);
    }

    @Test
    @Requirement("TRK-010")
    void satelliteOutsideTheCatalogIsNotFound() {
        given(service.track(any(), any())).willThrow(new SatelliteNotInCatalogException(ISS));

        assertThat(mvc.get().uri(URI)).hasStatus(HttpStatus.NOT_FOUND);
    }
}
