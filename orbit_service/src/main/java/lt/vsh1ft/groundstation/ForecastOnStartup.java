package lt.vsh1ft.groundstation;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.pass.NoradId;
import lt.vsh1ft.groundstation.pass.PassForecastService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ForecastOnStartup implements CommandLineRunner {

    private final PassForecastService service;
    private final GroundStationProperties properties;

    @Override
    public void run(String... args) {
        service.forecast(
                new NoradId(properties.forecast().satellite()),
                properties.station(),
                properties.forecast().duration());
    }
}
