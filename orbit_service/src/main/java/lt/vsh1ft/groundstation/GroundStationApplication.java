package lt.vsh1ft.groundstation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@Modulithic(sharedModules = "shared")
@EnableScheduling
public class GroundStationApplication {

    public static void main(String[] args) {
        SpringApplication.run(GroundStationApplication.class, args);
    }
}
