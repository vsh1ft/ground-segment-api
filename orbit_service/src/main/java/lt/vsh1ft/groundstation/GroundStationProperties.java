package lt.vsh1ft.groundstation;

import java.time.Duration;

import lt.vsh1ft.groundstation.pass.GroundStation;
import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties("groundstation")
public record GroundStationProperties(GroundStation station, Forecast forecast) {

    public record Forecast(int satellite, Duration duration) {
    }
}
