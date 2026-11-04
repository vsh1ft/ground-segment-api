package lt.vsh1ft.groundstation.elements;

import java.time.Clock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.orekit.propagation.analytical.tle.TLE;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoredTleSource implements TleSource {

    private final OmmDownloader downloader;
    private final CelestrakProperties properties;
    private final StoredOmmRepository repository;
    private final Clock clock;

    @Override
    public TLE latest(NoradId satellite) {
        final var stored = repository.findFirstByNoradIdOrderByDownloadedAtDesc(satellite.value());
        if (stored.filter(this::isFresh).isPresent()) {
            return OmmTleConverter.toTle(stored.get());
        }
        try {
            return downloadAndSave(satellite);
        } catch (CelestrakException e) {
            return stored.map(previous -> fallBackTo(previous, e))
                    .orElseThrow(() -> new OrbitDataUnavailableException(
                            "No stored orbit data for NORAD ID " + satellite + " and CelesTrak is unavailable", e));
        }
    }

    private TLE downloadAndSave(NoradId satellite) {
        final var downloaded = downloader.download(satellite);
        save(downloaded);
        log.info("Downloaded the OMM for {} from CelesTrak, epoch {}", satellite, downloaded.epoch());
        return OmmTleConverter.toTle(downloaded);
    }

    private void save(StoredOmm downloaded) {
        final var row = repository.findByNoradIdAndEpoch(downloaded.noradId(), downloaded.epoch())
                .map(existing -> existing.withDownloadedAt(downloaded.downloadedAt()))
                .orElse(downloaded);
        repository.save(row);
    }

    private TLE fallBackTo(StoredOmm previous, CelestrakException e) {
        log.warn("{}; using the OMM downloaded at {}", e.getMessage(), previous.downloadedAt());
        return OmmTleConverter.toTle(previous);
    }

    private boolean isFresh(StoredOmm omm) {
        return omm.downloadedAt().plus(properties.refresh()).isAfter(clock.instant());
    }
}
