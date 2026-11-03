package lt.vsh1ft.groundstation.api;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.station.Station;
import lt.vsh1ft.groundstation.station.StationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequiredArgsConstructor
public class StationController implements StationsApi {

    private final StationService service;

    @Override
    public List<Station> all() {
        return service.all();
    }

    @Override
    public Station get(UUID id) {
        return service.get(id);
    }

    @Override
    public ResponseEntity<IdResponse> create(StationRequest request, UriComponentsBuilder uri) {
        final var id = service.create(request.toGroundStation()).id();
        return ResponseEntity.created(uri.path("/stations/{id}").build(id)).body(new IdResponse(id));
    }

    @Override
    public void update(UUID id, StationRequest request) {
        service.update(id, request.toGroundStation());
    }

    @Override
    public void delete(UUID id) {
        service.delete(id);
    }
}
