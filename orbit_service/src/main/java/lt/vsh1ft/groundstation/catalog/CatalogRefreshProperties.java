package lt.vsh1ft.groundstation.catalog;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("groundstation.catalog.refresh")
record CatalogRefreshProperties(Duration interval, List<String> groups) {
}
