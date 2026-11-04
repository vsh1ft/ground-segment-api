package lt.vsh1ft.groundstation.catalog;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

interface SatelliteSummaryRepository extends Repository<SatelliteSummary, Integer> {

    Optional<SatelliteSummary> findById(int noradId);

    Page<SatelliteSummary> findAll(Pageable pageable);

    Page<SatelliteSummary> findByNoradId(int noradId, Pageable pageable);

    Page<SatelliteSummary> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
