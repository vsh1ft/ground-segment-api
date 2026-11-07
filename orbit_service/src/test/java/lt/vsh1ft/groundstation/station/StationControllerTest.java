package lt.vsh1ft.groundstation.station;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.UUID;

import lt.vsh1ft.groundstation.pass.GroundStation;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(StationController.class)
class StationControllerTest {

    private static final UUID VILNIUS_ID = UUID.fromString("3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42");
    private static final GroundStation VILNIUS = new GroundStation("Vilnius", 54.67519, 25.23422, 114.0, 5.0);
    private static final Station STORED_VILNIUS = new Station(VILNIUS_ID, "Vilnius", 54.67519, 25.23422, 114.0, 5.0);
    private static final String VILNIUS_JSON = """
            {"name": "Vilnius", "latitudeDeg": 54.67519, "longitudeDeg": 25.23422, "altitudeM": 114,
             "minElevationDeg": 5}
            """;
    private static final String STORED_VILNIUS_JSON = """
            {"id": "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42", "name": "Vilnius", "latitudeDeg": 54.67519,
             "longitudeDeg": 25.23422, "altitudeM": 114.0, "minElevationDeg": 5.0}
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private StationService service;

    @Test
    @Requirement("STN-010")
    void stationsAreListed() {
        given(service.all()).willReturn(List.of(STORED_VILNIUS));

        assertThat(mvc.get().uri("/stations")).hasStatusOk().bodyJson()
                .isStrictlyEqualTo("[" + STORED_VILNIUS_JSON + "]");
    }

    @Test
    @Requirement("STN-010")
    void stationIsReturnedById() {
        given(service.get(VILNIUS_ID)).willReturn(STORED_VILNIUS);

        assertThat(mvc.get().uri("/stations/{id}", VILNIUS_ID)).hasStatusOk().bodyJson()
                .isStrictlyEqualTo(STORED_VILNIUS_JSON);
    }

    @Test
    @Requirement("STN-040")
    void unknownStationIsNotFound() {
        given(service.get(VILNIUS_ID)).willThrow(new StationNotFoundException(VILNIUS_ID));

        assertThat(mvc.get().uri("/stations/{id}", VILNIUS_ID)).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("No station with ID " + VILNIUS_ID);
    }

    @Test
    @Requirement("STN-010")
    void createReturnsOnlyTheNewIdAndItsLocation() {
        given(service.create(VILNIUS)).willReturn(STORED_VILNIUS);

        final var response = mvc.post().uri("/stations")
                .contentType(MediaType.APPLICATION_JSON).content(VILNIUS_JSON).exchange();

        assertThat(response).hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "http://localhost/stations/" + VILNIUS_ID)
                .bodyJson().isStrictlyEqualTo("""
                        {"id": "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42"}
                        """);
    }

    @Test
    @Requirement("STN-020")
    void duplicateNameIsAConflict() {
        given(service.create(VILNIUS)).willThrow(new StationNameTakenException("Vilnius"));

        assertThat(mvc.post().uri("/stations").contentType(MediaType.APPLICATION_JSON).content(VILNIUS_JSON))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").isEqualTo("A station named 'Vilnius' already exists");
    }

    @Test
    @Requirement({"STN-030", "API-010"})
    void invalidStationIsABadRequestNamingEveryField() {
        final var response = mvc.post().uri("/stations").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": " ", "latitudeDeg": 91, "longitudeDeg": 25, "minElevationDeg": -6}
                        """)
                .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().isStrictlyEqualTo("""
                        {
                          "title": "Bad Request",
                          "status": 400,
                          "detail": "Invalid request content.",
                          "instance": "/stations",
                          "errors": [
                            {"field": "altitudeM", "message": "must not be null"},
                            {"field": "latitudeDeg", "message": "must be between -90 and 90"},
                            {"field": "minElevationDeg", "message": "must be between -5 and 90"},
                            {"field": "name", "message": "must not be blank"}
                          ]
                        }
                        """);
        verifyNoInteractions(service);
    }

    @Test
    @Requirement("STN-010")
    void stationIsReplacedWithoutResponseBody() {
        final var response = mvc.put().uri("/stations/{id}", VILNIUS_ID)
                .contentType(MediaType.APPLICATION_JSON).content(VILNIUS_JSON).exchange();

        assertThat(response).hasStatus(HttpStatus.NO_CONTENT).body().isEmpty();
        verify(service).update(VILNIUS_ID, VILNIUS);
    }

    @Test
    @Requirement("STN-010")
    void stationIsDeleted() {
        assertThat(mvc.delete().uri("/stations/{id}", VILNIUS_ID)).hasStatus(HttpStatus.NO_CONTENT);
        verify(service).delete(VILNIUS_ID);
    }

    @Test
    @Requirement("STN-040")
    void deletingAnUnknownStationIsNotFound() {
        willThrow(new StationNotFoundException(VILNIUS_ID)).given(service).delete(VILNIUS_ID);

        assertThat(mvc.delete().uri("/stations/{id}", VILNIUS_ID)).hasStatus(HttpStatus.NOT_FOUND);
    }
}
