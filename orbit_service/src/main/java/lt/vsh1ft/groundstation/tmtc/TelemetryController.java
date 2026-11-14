package lt.vsh1ft.groundstation.tmtc;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TelemetryController implements TelemetryApi {

    private final TmPacketArchive archive;

    @Override
    public TmPacketsResponse packets(int noradId, PacketsQuery query) {
        return TmPacketsResponse.of(archive.packets(new NoradId(noradId), query));
    }
}
