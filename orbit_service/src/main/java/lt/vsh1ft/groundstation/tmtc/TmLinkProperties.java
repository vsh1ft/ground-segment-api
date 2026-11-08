package lt.vsh1ft.groundstation.tmtc;

import java.time.Duration;

import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("groundstation.tmtc.link")
record TmLinkProperties(int noradId, String host, int port, Duration connectTimeout, Duration reconnectDelay,
                        Resource missionDatabase) {

    NoradId satellite() {
        return new NoradId(noradId);
    }
}
