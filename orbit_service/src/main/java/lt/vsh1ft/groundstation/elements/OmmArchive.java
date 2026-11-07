package lt.vsh1ft.groundstation.elements;

import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OmmArchive {

    private final StoredOmmRepository repository;

    public void save(StoredOmm downloaded) {
        final var row = repository.findByNoradIdAndEpoch(downloaded.noradId(), downloaded.epoch())
                .map(existing -> existing.withDownloadedAt(downloaded.downloadedAt()))
                .orElse(downloaded);
        repository.save(row);
    }

    public Optional<Instant> lastDownload() {
        return repository.findFirstByOrderByDownloadedAtDesc().map(StoredOmm::downloadedAt);
    }
}
