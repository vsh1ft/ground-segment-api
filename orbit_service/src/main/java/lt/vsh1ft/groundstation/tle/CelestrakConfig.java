package lt.vsh1ft.groundstation.tle;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class CelestrakConfig {

    @Bean
    public RestClient celestrakRestClient(RestClient.Builder builder, CelestrakProperties properties) {
        return builder.baseUrl(properties.baseUrl()).build();
    }
}
