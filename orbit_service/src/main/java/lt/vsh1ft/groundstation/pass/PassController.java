package lt.vsh1ft.groundstation.pass;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.catalog.SatelliteService;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PassController implements PassesApi {

    private final PassForecastService service;
    private final GroundStationSource stations;
    private final SatelliteService satellites;

    @Override
    public PassesResponse passes(PassQuery query) {
        final var satellite = new NoradId(query.satellite());
        satellites.ensureInCatalog(satellite);
        final var station = stations.byId(query.station());

        final var forecast = service.forecast(satellite, station, query.duration());
        return PassesResponse.of(satellite, query.station(), forecast);
    }
}
