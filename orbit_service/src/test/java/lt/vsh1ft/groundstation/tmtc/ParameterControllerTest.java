package lt.vsh1ft.groundstation.tmtc;

import static lt.vsh1ft.groundstation.tmtc.SimulatorTelemetry.SATELLITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(ParameterController.class)
class ParameterControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ParameterService service;

    @Test
    @Requirement("PAR-040")
    void parametersAreListedWithKindUnitAndLabels() {
        given(service.parameters(SATELLITE)).willReturn(List.of(
                new ParameterResponse("BATTERY_VOLTAGE", "Battery voltage", ParameterResponse.Kind.FLOAT, "V", null),
                new ParameterResponse("SPACECRAFT_MODE", "Spacecraft mode", ParameterResponse.Kind.ENUMERATED, null,
                        Map.of(0L, "SAFE"))));

        assertThat(mvc.get().uri("/satellites/99999/parameters")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                [{"name": "BATTERY_VOLTAGE", "description": "Battery voltage", "kind": "FLOAT", "unit": "V",
                  "labels": null},
                 {"name": "SPACECRAFT_MODE", "description": "Spacecraft mode", "kind": "ENUMERATED", "unit": null,
                  "labels": {"0": "SAFE"}}]
                """);
    }

    @Test
    @Requirement("PAR-050")
    void valuesAreListedWithRawAndEngineeringValueAndTheCursor() {
        final var query = new ValuesQuery(1, 50L, Instant.parse("2026-11-08T08:00:00Z"),
                Instant.parse("2026-11-08T09:00:00Z"));
        given(service.values(SATELLITE, "BATTERY_VOLTAGE", query)).willReturn(new ParameterValuesResponse(
                "BATTERY_VOLTAGE", "V", List.of(new ParameterValuesResponse.Value(42,
                        Instant.parse("2026-11-08T08:53:41.923Z"), 7411, 7.411, 12L, null)), 42L));

        assertThat(mvc.get().uri("/satellites/99999/parameters/BATTERY_VOLTAGE/values?limit=1&before=50"
                + "&from=2026-11-08T08:00:00Z&to=2026-11-08T09:00:00Z"))
                .hasStatusOk().bodyJson().isStrictlyEqualTo("""
                        {"parameter": "BATTERY_VOLTAGE", "unit": "V",
                         "values": [{"id": 42, "generationTime": "2026-11-08T08:53:41.923Z", "raw": 7411,
                                     "engineering": 7.411, "packetId": 12, "frameId": null}],
                         "nextBefore": 42}
                        """);
    }

    @Test
    @Requirement("PAR-040")
    void satelliteWithoutAMissionDatabaseIsNotFound() {
        given(service.parameters(new NoradId(25544))).willThrow(new NoMissionDatabaseException(new NoradId(25544)));

        assertThat(mvc.get().uri("/satellites/25544/parameters")).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().isLenientlyEqualTo("""
                        {"status": 404, "detail": "No mission database for satellite 25544"}
                        """);
    }

    @Test
    @Requirement({"PAR-050", "API-010"})
    void invalidValueQueryIsABadRequestNamingEveryParameter() {
        final var response = mvc.get().uri("/satellites/99999/parameters/BATTERY_VOLTAGE/values?limit=0&before=-1")
                .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().isLenientlyEqualTo("""
                        {"errors": [{"field": "before", "message": "must be greater than 0"},
                                    {"field": "limit", "message": "must be greater than or equal to 1"}]}
                        """);
        verifyNoInteractions(service);
    }

    @Test
    @Requirement({"PAR-050", "API-010"})
    void limitOfOneHundredIsAcceptedAndOneHundredAndOneIsNot() {
        given(service.values(SATELLITE, "BATTERY_VOLTAGE", new ValuesQuery(100, null, null, null)))
                .willReturn(new ParameterValuesResponse("BATTERY_VOLTAGE", "V", List.of(), null));

        assertThat(mvc.get().uri("/satellites/99999/parameters/BATTERY_VOLTAGE/values?limit=100")).hasStatusOk();
        assertThat(mvc.get().uri("/satellites/99999/parameters/BATTERY_VOLTAGE/values?limit=101"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isLenientlyEqualTo("""
                        {"errors": [{"field": "limit", "message": "must be less than or equal to 100"}]}
                        """);
    }
}
