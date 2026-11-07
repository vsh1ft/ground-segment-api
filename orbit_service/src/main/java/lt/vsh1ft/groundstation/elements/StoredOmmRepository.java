package lt.vsh1ft.groundstation.elements;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.ListCrudRepository;

public interface StoredOmmRepository extends ListCrudRepository<StoredOmm, Long> {

    Optional<StoredOmm> findFirstByNoradIdOrderByEpochDesc(int noradId);

    Optional<StoredOmm> findFirstByOrderByDownloadedAtDesc();

    Optional<StoredOmm> findByNoradIdAndEpoch(int noradId, LocalDateTime epoch);

    Page<StoredOmm> findByNoradIdOrderByEpochDesc(int noradId, Pageable pageable);
}
