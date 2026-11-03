package lt.vsh1ft.groundstation;

import lt.vsh1ft.groundstation.pass.GroundStation;
import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties("groundstation")
public record GroundStationProperties(GroundStation station) {
}
