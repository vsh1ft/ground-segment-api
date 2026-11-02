package lt.vsh1ft.groundstation;

import lt.vsh1ft.groundstation.pass.NoradId;
import lt.vsh1ft.groundstation.pass.PassForecastService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GroundStationApplication {

    public static void main(String[] args) {
        SpringApplication.run(GroundStationApplication.class, args);
    }

    @Bean
    CommandLineRunner forecastOnStartup(PassForecastService service, GroundStationProperties properties) {
        return args -> service.forecast(
                new NoradId(properties.forecast().satellite()),
                properties.station(),
                properties.forecast().duration());
    }
}
