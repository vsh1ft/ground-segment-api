package lt.vsh1ft.groundstation.tle;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("groundstation.celestrak")
public record CelestrakProperties(String baseUrl, Duration refresh, Duration connectTimeout, Duration readTimeout) {
}
