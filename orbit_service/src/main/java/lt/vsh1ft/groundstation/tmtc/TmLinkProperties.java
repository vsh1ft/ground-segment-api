package lt.vsh1ft.groundstation.tmtc;

import java.time.Duration;

import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("groundstation.tmtc.link")
record TmLinkProperties(int noradId, String host, int port, Duration connectTimeout, Duration reconnectDelay) {

    NoradId satellite() {
        return new NoradId(noradId);
    }
}
