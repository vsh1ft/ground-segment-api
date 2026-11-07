package lt.vsh1ft.groundstation.radio;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("groundstation.radio.sync")
record TransmitterSyncProperties(Duration interval) {
}
