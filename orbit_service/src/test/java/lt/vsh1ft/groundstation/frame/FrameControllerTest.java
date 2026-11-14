package lt.vsh1ft.groundstation.frame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lt.vsh1ft.groundstation.catalog.SatelliteNotInCatalogException;
import lt.vsh1ft.groundstation.catalog.SatelliteService;
import lt.vsh1ft.groundstation.station.GroundStation;
import lt.vsh1ft.groundstation.station.GroundStationSource;
import lt.vsh1ft.groundstation.shared.CursorPage;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.station.StationNotFoundException;
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
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(FrameController.class)
class FrameControllerTest {

    private static final NoradId FRONTIERSAT = new NoradId(69015);
    private static final UUID STATION = UUID.fromString("3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42");
    private static final GroundStation VILNIUS = new GroundStation("Vilnius", 54.67519, 25.23422, 114.0, 5.0);
    private static final Instant HEARD = Instant.parse("2026-11-08T19:03:34.120Z");
    private static final ReceivedFrame FRAME = new ReceivedFrame(4712, HEARD, "BEACON_EXTENDED_V5",
            Instant.parse("2026-11-08T13:51:24.338Z"), true, new byte[] {(byte) 0xC2, (byte) 0xA2, (byte) 0x8A},
            List.of(new Reception(ReceptionSource.OWN, STATION, "Vilnius", 54.67519, 25.23422, 114.0, null, HEARD)));
    private static final String UPLOAD = """
            {"station": "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42", "receivedAt": "2026-11-08T19:03:34.120Z",
             "frame": "c2a28a"}
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private FrameReception reception;

    @MockitoBean
    private FrameArchive archive;

    @MockitoBean
    private SatelliteService satellites;

    @MockitoBean
    private GroundStationSource stations;

    @Test
    @Requirement("SAT-040")
    void framesAreListedWithTheirReceptions() {
        final var query = new FramesQuery(5, 4713L, ReceptionSource.OWN, STATION);
        given(archive.frames(FRONTIERSAT, query)).willReturn(new CursorPage<>(List.of(FRAME), null));

        assertThat(mvc.get().uri("/satellites/69015/frames?limit=5&before=4713&source=OWN&station=" + STATION))
                .hasStatusOk().bodyJson().isStrictlyEqualTo("""
                        {"frames": [{"id": 4712, "firstReceivedAt": "2026-11-08T19:03:34.120Z",
                                     "container": "BEACON_EXTENDED_V5", "generationTime": "2026-11-08T13:51:24.338Z",
                                     "crcChecked": true, "length": 3, "raw": "C2A28A",
                                     "receptions": [{"source": "OWN", "station": "Vilnius",
                                                     "stationId": "3f2a9c1e-7b4d-4e8a-9c2f-1a6b5d8e0f42",
                                                     "latitudeDeg": 54.67519, "longitudeDeg": 25.23422,
                                                     "altitudeM": 114.0, "observation": null,
                                                     "receivedAt": "2026-11-08T19:03:34.120Z"}]}],
                         "nextBefore": null}
                        """);
    }

    @Test
    @Requirement("SAT-040")
    void limitOfOneHundredIsAcceptedAndZeroAndOneHundredAndOneAreNot() {
        given(archive.frames(any(), any())).willReturn(new CursorPage<>(List.of(), null));

        assertThat(mvc.get().uri("/satellites/69015/frames?limit=100")).hasStatusOk();
        assertThat(mvc.get().uri("/satellites/69015/frames?limit=0")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/satellites/69015/frames?limit=101")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Requirement("SAT-050")
    void frameTheServiceHadNotSeenIsCreated() {
        given(stations.byId(STATION)).willReturn(VILNIUS);
        given(reception.receive(eq(FRONTIERSAT), eq(new byte[] {(byte) 0xC2, (byte) 0xA2, (byte) 0x8A}),
                eq(Reception.own(STATION, VILNIUS, HEARD)))).willReturn(new FrameReception.Outcome(4712, true, true));
        given(archive.frame(4712)).willReturn(FRAME);

        assertThat(post(UPLOAD)).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.id").isEqualTo(4712);
    }

    @Test
    @Requirement("SAT-050")
    void frameAlreadyReceivedIsReturnedWithItsReceptions() {
        given(stations.byId(STATION)).willReturn(VILNIUS);
        given(reception.receive(any(), any(), any())).willReturn(new FrameReception.Outcome(4712, false, false));
        given(archive.frame(4712)).willReturn(FRAME);

        assertThat(post(UPLOAD)).hasStatusOk().bodyJson().extractingPath("$.receptions[0].source").isEqualTo("OWN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "c2a", "zz", "not hex"})
    @Requirement({"SAT-050", "API-010"})
    void frameThatIsNotWholeBytesInHexIsABadRequest(String frame) {
        assertThat(post(UPLOAD.replace("c2a28a", frame))).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .isLenientlyEqualTo("{\"errors\": [{\"field\": \"frame\", \"message\": \"must be 1 to 2048 bytes in hex\"}]}");
        verifyNoInteractions(reception, satellites, stations);
    }

    @Test
    @Requirement("SAT-050")
    void frameOf2048BytesIsAcceptedAndOneByteMoreIsNot() {
        given(stations.byId(STATION)).willReturn(VILNIUS);
        given(reception.receive(any(), any(), any())).willReturn(new FrameReception.Outcome(4712, true, true));
        given(archive.frame(4712)).willReturn(FRAME);

        assertThat(post(UPLOAD.replace("c2a28a", "AB".repeat(2048)))).hasStatus(HttpStatus.CREATED);
        assertThat(post(UPLOAD.replace("c2a28a", "AB".repeat(2049)))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Requirement({"SAT-050", "API-010"})
    void missingStationOrTimeIsABadRequest() {
        assertThat(post("{\"frame\": \"c2a28a\"}")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .isLenientlyEqualTo("""
                        {"errors": [{"field": "receivedAt", "message": "must not be null"},
                                    {"field": "station", "message": "must not be null"}]}
                        """);
    }

    @Test
    @Requirement("SAT-050")
    void unknownStationOrSatelliteOutsideTheCatalogIsNotFound() {
        given(stations.byId(STATION)).willThrow(new StationNotFoundException(STATION));

        assertThat(post(UPLOAD)).hasStatus(HttpStatus.NOT_FOUND);
        willThrow(new SatelliteNotInCatalogException(FRONTIERSAT)).given(satellites).ensureInCatalog(FRONTIERSAT);
        assertThat(post(UPLOAD)).hasStatus(HttpStatus.NOT_FOUND);
        verifyNoInteractions(reception);
    }

    private MvcTestResult post(String body) {
        return mvc.post().uri("/satellites/69015/frames").contentType(MediaType.APPLICATION_JSON).content(body)
                .exchange();
    }
}
