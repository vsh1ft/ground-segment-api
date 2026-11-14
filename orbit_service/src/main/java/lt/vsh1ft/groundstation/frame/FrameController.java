package lt.vsh1ft.groundstation.frame;

import java.util.HexFormat;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.catalog.SatelliteService;
import lt.vsh1ft.groundstation.station.GroundStationSource;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class FrameController implements FramesApi {

    private final FrameReception reception;
    private final FrameArchive archive;
    private final SatelliteService satellites;
    private final GroundStationSource stations;

    @Override
    public FramesResponse frames(int noradId, FramesQuery query) {
        return FramesResponse.of(archive.frames(new NoradId(noradId), query));
    }

    @Override
    public ResponseEntity<FrameResponse> upload(int noradId, FrameUpload upload) {
        final var satellite = new NoradId(noradId);
        satellites.ensureInCatalog(satellite);
        final var station = stations.byId(upload.station());
        final var outcome = reception.receive(satellite, HexFormat.of().parseHex(upload.frame()),
                Reception.own(upload.station(), station, upload.receivedAt()));
        return ResponseEntity.status(outcome.newFrame() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(FrameResponse.from(archive.frame(outcome.frameId())));
    }
}
