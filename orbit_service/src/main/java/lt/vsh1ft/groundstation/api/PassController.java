package lt.vsh1ft.groundstation.api;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.pass.NoradId;
import lt.vsh1ft.groundstation.pass.PassForecastService;
import lt.vsh1ft.groundstation.station.StationService;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PassController implements PassesApi {

    private final PassForecastService service;
    private final StationService stations;

    @Override
    public PassesResponse passes(PassQuery query) {
        final var satellite = new NoradId(query.satellite());
        final var station = stations.get(query.station());

        final var passes = service.forecast(satellite, station.toGroundStation(), query.duration());
        return PassesResponse.of(satellite, station, passes);
    }
}
