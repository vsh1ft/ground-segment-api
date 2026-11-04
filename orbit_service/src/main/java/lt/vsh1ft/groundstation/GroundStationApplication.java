package lt.vsh1ft.groundstation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;

@SpringBootApplication
@ConfigurationPropertiesScan
@Modulithic(sharedModules = "shared")
public class GroundStationApplication {

    public static void main(String[] args) {
        SpringApplication.run(GroundStationApplication.class, args);
    }
}
