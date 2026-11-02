package lt.vsh1ft.groundstation.orekit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("groundstation.pass-detection")
public record PassDetectionProperties(double minElevationDeg, double maxCheckS, double thresholdS) {
}
