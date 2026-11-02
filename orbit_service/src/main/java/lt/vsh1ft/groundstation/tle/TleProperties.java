package lt.vsh1ft.groundstation.tle;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("groundstation")
public record TleProperties(List<TleLines> tles) {
}
