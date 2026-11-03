package lt.vsh1ft.groundstation.api;

import java.util.Optional;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi(ObjectProvider<BuildProperties> build) {
        final var version = Optional.ofNullable(build.getIfAvailable()).map(BuildProperties::getVersion).orElse("dev");
        return new OpenAPI().info(new Info()
                .title("ground-segment-api")
                .version(version)
                .description("Ground-segment backend: ground stations and satellite pass predictions. "
                        + "Orbital elements come from CelesTrak and are propagated with Orekit (SGP4). "
                        + "All times are UTC. Errors are RFC 9457 problem details.")
                .license(new License()
                        .name("Apache License 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
