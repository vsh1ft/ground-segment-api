package lt.vsh1ft.groundstation.satnogs;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class SatnogsNetworkConfig {

    static final String USER_AGENT = "ground-segment-api (+https://github.com/vsh1ft/ground-segment-api)";

    @Bean
    RestClient satnogsNetworkRestClient(RestClient.Builder builder, SatnogsNetworkProperties properties) {
        final var httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        final var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .requestFactory(requestFactory)
                .build();
    }
}
