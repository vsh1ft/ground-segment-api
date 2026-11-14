package lt.vsh1ft.groundstation.radio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;

import lt.vsh1ft.groundstation.catalog.SatelliteNotInCatalogException;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(TransmitterController.class)
class TransmitterControllerTest {

    private static final NoradId ISS = new NoradId(25544);
    private static final Transmitter ISS_APRS = new Transmitter("ZJxCeQmih9zDfYNVrB4wRN", 25544,
            "Mode V APRS", TransmitterType.TRANSCEIVER, TransmitterStatus.ACTIVE, 145825000L, null, 145825000L, null,
            "AFSK", "AFSK", 1200.0, false, Instant.parse("2019-06-16T08:49:33.586159Z"),
            Instant.parse("2026-11-08T00:00:00Z"));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TransmitterService service;

    @Test
    @Requirement("RAD-030")
    void transmittersOfASatelliteAreListed() {
        given(service.transmitters(ISS, null)).willReturn(List.of(ISS_APRS));

        assertThat(mvc.get().uri("/satellites/25544/transmitters")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                [{"uuid": "ZJxCeQmih9zDfYNVrB4wRN", "description": "Mode V APRS", "type": "TRANSCEIVER",
                  "status": "ACTIVE", "uplinkLowHz": 145825000, "uplinkHighHz": null, "downlinkLowHz": 145825000,
                  "downlinkHighHz": null, "mode": "AFSK", "uplinkMode": "AFSK", "baud": 1200.0, "invert": false,
                  "updatedAt": "2019-06-16T08:49:33.586159Z"}]
                """);
    }

    @Test
    @Requirement("RAD-030")
    void statusFilterIsPassedOn() {
        given(service.transmitters(ISS, TransmitterStatus.ACTIVE)).willReturn(List.of(ISS_APRS));

        assertThat(mvc.get().uri("/satellites/25544/transmitters?status=ACTIVE")).hasStatusOk()
                .bodyJson().extractingPath("$[0].uuid").isEqualTo("ZJxCeQmih9zDfYNVrB4wRN");
    }

    @Test
    @Requirement({"RAD-030", "API-010"})
    void unknownStatusIsABadRequest() {
        final var response = mvc.get().uri("/satellites/25544/transmitters?status=BROKEN").exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        verifyNoInteractions(service);
    }

    @Test
    @Requirement("CAT-090")
    void satelliteOutsideTheCatalogIsNotFound() {
        given(service.transmitters(ISS, null)).willThrow(new SatelliteNotInCatalogException(ISS));

        assertThat(mvc.get().uri("/satellites/25544/transmitters")).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("No satellite with NORAD ID 25544 in the catalog");
    }
}
