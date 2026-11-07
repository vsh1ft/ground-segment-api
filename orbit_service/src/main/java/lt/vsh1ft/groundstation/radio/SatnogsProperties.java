package lt.vsh1ft.groundstation.radio;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("groundstation.satnogs")
record SatnogsProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
