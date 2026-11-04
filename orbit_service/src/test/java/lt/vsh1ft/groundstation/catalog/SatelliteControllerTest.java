package lt.vsh1ft.groundstation.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;

import lt.vsh1ft.groundstation.elements.OrbitDataUnavailableException;
import lt.vsh1ft.groundstation.elements.SatelliteNotFoundException;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(SatelliteController.class)
class SatelliteControllerTest {

    private static final NoradId ISS = new NoradId(25544);
    private static final SatelliteSummary ISS_SUMMARY = new SatelliteSummary(25544, "ISS (ZARYA)", "1998-067A",
            Instant.parse("2026-11-04T10:15:30Z"), Instant.parse("2026-11-04T06:12:45.123Z"));
    private static final String ISS_JSON = """
            {"noradId": 25544, "name": "ISS (ZARYA)", "internationalDesignator": "1998-067A",
             "addedAt": "2026-11-04T10:15:30Z", "latestEpoch": "2026-11-04T06:12:45.123Z"}
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SatelliteService service;

    @Test
    void searchReturnsOnePageWithPagingMetadata() {
        given(service.search("iss", PageRequest.of(1, 1)))
                .willReturn(new PageImpl<>(List.of(ISS_SUMMARY), PageRequest.of(1, 1), 3));

        assertThat(mvc.get().uri("/satellites?search=iss&page=1&size=1")).hasStatusOk().bodyJson()
                .isStrictlyEqualTo("""
                        {"content": [%s],
                         "page": {"size": 1, "number": 1, "totalElements": 3, "totalPages": 3}}
                        """.formatted(ISS_JSON));
    }

    @Test
    void searchDefaultsToTheFirstPageOfTwenty() {
        given(service.search(null, PageRequest.of(0, 20))).willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertThat(mvc.get().uri("/satellites")).hasStatusOk()
                .bodyJson().extractingPath("$.page.size").isEqualTo(20);
    }

    @Test
    void invalidPagingIsABadRequestNamingEveryParameter() {
        final var response = mvc.get().uri("/satellites?page=-1&size=101").exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().isLenientlyEqualTo("""
                        {"errors": [{"field": "page", "message": "must be greater than or equal to 0"},
                                    {"field": "size", "message": "must be less than or equal to 100"}]}
                        """);
        verifyNoInteractions(service);
    }

    @Test
    void satelliteIsReturnedByNoradId() {
        given(service.get(ISS)).willReturn(ISS_SUMMARY);

        assertThat(mvc.get().uri("/satellites/25544")).hasStatusOk().bodyJson().isStrictlyEqualTo(ISS_JSON);
    }

    @Test
    void satelliteOutsideTheCatalogIsNotFound() {
        given(service.get(ISS)).willThrow(new SatelliteNotInCatalogException(ISS));

        assertThat(mvc.get().uri("/satellites/25544")).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("No satellite with NORAD ID 25544 in the catalog");
    }

    @Test
    void zeroNoradIdIsABadRequest() {
        final var response = mvc.get().uri("/satellites/0").exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().isLenientlyEqualTo("""
                        {"errors": [{"field": "noradId", "message": "must be greater than 0"}]}
                        """);
        verifyNoInteractions(service);
    }

    @Test
    void addedSatelliteHasALocationAndNoBody() {
        final var response = mvc.post().uri("/satellites/25544").exchange();

        assertThat(response).hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "http://localhost/satellites/25544")
                .body().isEmpty();
        verify(service).add(ISS);
    }

    @Test
    void addingASatelliteAlreadyInTheCatalogIsAConflict() {
        willThrow(new SatelliteAlreadyInCatalogException(ISS)).given(service).add(ISS);

        assertThat(mvc.post().uri("/satellites/25544")).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").isEqualTo("NORAD ID 25544 is already in the catalog");
    }

    @Test
    void addingASatelliteCelestrakDoesNotKnowIsNotFound() {
        willThrow(new SatelliteNotFoundException(ISS)).given(service).add(ISS);

        assertThat(mvc.post().uri("/satellites/25544")).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("No orbit data for NORAD ID 25544");
    }

    @Test
    void addingWhileCelestrakIsDownIsServiceUnavailable() {
        willThrow(new OrbitDataUnavailableException("CelesTrak is unavailable", null)).given(service).add(ISS);

        assertThat(mvc.post().uri("/satellites/25544")).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void satelliteIsDeleted() {
        assertThat(mvc.delete().uri("/satellites/25544")).hasStatus(HttpStatus.NO_CONTENT);
        verify(service).delete(ISS);
    }

    @Test
    void deletingASatelliteOutsideTheCatalogIsNotFound() {
        willThrow(new SatelliteNotInCatalogException(ISS)).given(service).delete(ISS);

        assertThat(mvc.delete().uri("/satellites/25544")).hasStatus(HttpStatus.NOT_FOUND);
    }
}
