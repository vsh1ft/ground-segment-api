package lt.vsh1ft.groundstation.api;

import java.time.Duration;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.GroundStationProperties;
import lt.vsh1ft.groundstation.pass.NoradId;
import lt.vsh1ft.groundstation.pass.PassForecastService;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PassController {

    private final PassForecastService service;
    private final GroundStationProperties properties;

    @GetMapping("/passes")
    public PassesResponse passes(@RequestParam @Positive int satellite,
                                 @RequestParam(defaultValue = "P1D")
                                 @DurationMin(nanos = 0, inclusive = false) @DurationMax(days = 7) Duration duration) {
        final var noradId = new NoradId(satellite);
        final var station = properties.station();

        final var passes = service.forecast(noradId, station, duration);
        return PassesResponse.of(noradId, station, passes);
    }
}
