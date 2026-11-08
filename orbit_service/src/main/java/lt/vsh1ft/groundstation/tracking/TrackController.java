package lt.vsh1ft.groundstation.tracking;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class TrackController implements TrackApi {

    private final TrackService service;

    @Override
    public TrackResponse track(int noradId, TrackQuery query) {
        return service.track(new NoradId(noradId), query);
    }
}
