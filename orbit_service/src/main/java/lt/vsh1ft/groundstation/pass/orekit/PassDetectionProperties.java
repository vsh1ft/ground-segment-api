package lt.vsh1ft.groundstation.pass.orekit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("groundstation.pass-detection")
public record PassDetectionProperties(double maxCheckS, double thresholdS) {
}
