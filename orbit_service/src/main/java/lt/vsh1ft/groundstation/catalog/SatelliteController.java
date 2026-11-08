package lt.vsh1ft.groundstation.catalog;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequiredArgsConstructor
public class SatelliteController implements SatellitesApi {

    private final SatelliteService service;

    @Override
    public PagedModel<SatelliteSummary> search(SatelliteQuery query) {
        return new PagedModel<>(service.search(query.search(), PageRequest.of(query.page(), query.size())));
    }

    @Override
    public SatelliteSummary get(int noradId) {
        return service.get(new NoradId(noradId));
    }

    @Override
    public PagedModel<ElementSet> elements(int noradId, ElementsQuery query) {
        return new PagedModel<>(service.elementHistory(new NoradId(noradId), PageRequest.of(query.page(), query.size())));
    }

    @Override
    public ResponseEntity<Void> add(int noradId, UriComponentsBuilder uri) {
        service.add(new NoradId(noradId));
        return ResponseEntity.created(uri.path("/satellites/{noradId}").build(noradId)).build();
    }

    @Override
    public ResponseEntity<ElementSet> upload(int noradId, String tle, UriComponentsBuilder uri) {
        final var uploaded = service.upload(new NoradId(noradId), tle);
        if (uploaded.addedToCatalog()) {
            return ResponseEntity.created(uri.path("/satellites/{noradId}").build(noradId)).body(uploaded.elements());
        }
        return ResponseEntity.ok(uploaded.elements());
    }

    @Override
    public void delete(int noradId) {
        service.delete(new NoradId(noradId));
    }
}
