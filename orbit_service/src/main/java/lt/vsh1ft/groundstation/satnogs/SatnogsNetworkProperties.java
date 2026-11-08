package lt.vsh1ft.groundstation.satnogs;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("groundstation.satnogs.network")
record SatnogsNetworkProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
