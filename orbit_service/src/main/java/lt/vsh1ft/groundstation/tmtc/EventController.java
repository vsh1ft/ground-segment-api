package lt.vsh1ft.groundstation.tmtc;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EventController implements EventsApi {

    private final EventService service;

    @Override
    public EventsResponse events(int noradId, EventsQuery query) {
        return service.events(new NoradId(noradId), query);
    }
}
