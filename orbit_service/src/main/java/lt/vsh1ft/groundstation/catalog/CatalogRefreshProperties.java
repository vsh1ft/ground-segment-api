package lt.vsh1ft.groundstation.catalog;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("groundstation.catalog.refresh")
record CatalogRefreshProperties(Duration interval, List<String> groups) {

    static final Duration MINIMUM_INTERVAL = Duration.ofHours(2);

    CatalogRefreshProperties {
        Objects.requireNonNull(interval, "groundstation.catalog.refresh.interval is required");
        if (interval.compareTo(MINIMUM_INTERVAL) < 0) {
            throw new IllegalArgumentException("groundstation.catalog.refresh.interval must be at least "
                    + MINIMUM_INTERVAL + " because CelesTrak asks not to download the same data more often, was "
                    + interval);
        }
    }
}
