package lt.vsh1ft.groundstation.elements;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;

public interface StoredOmmRepository extends ListCrudRepository<StoredOmm, Long> {

    Optional<StoredOmm> findFirstByNoradIdOrderByDownloadedAtDesc(int noradId);

    Optional<StoredOmm> findByNoradIdAndEpoch(int noradId, LocalDateTime epoch);
}
