package lt.vsh1ft.groundstation.elements;

import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    public Page<StoredOmm> history(NoradId satellite, Pageable page) {
        return repository.findByNoradIdOrderByEpochDesc(satellite.value(), page);
    }

    public Optional<Instant> lastDownload() {
        return repository.findFirstByOrderByDownloadedAtDesc().map(StoredOmm::downloadedAt);
    }
}
