package lt.vsh1ft.groundstation.radio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.catalog.SatelliteService;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransmitterService {

    private final TransmitterRepository repository;
    private final JdbcAggregateOperations aggregates;
    private final SatelliteService catalog;

    public List<TransmitterResponse> transmitters(NoradId satellite, TransmitterStatus status) {
        catalog.ensureInCatalog(satellite);
        final var found = status == null
                ? repository.findByNoradIdOrderByDownlinkLowHzAscDescriptionAsc(satellite.value())
                : repository.findByNoradIdAndStatusOrderByDownlinkLowHzAscDescriptionAsc(satellite.value(), status);
        return found.stream().map(TransmitterResponse::from).toList();
    }

    @Transactional
    public void replaceAll(List<Transmitter> transmitters) {
        repository.deleteAll();
        aggregates.insertAll(transmitters);
    }

    public Optional<Instant> lastSync() {
        return repository.findFirstByOrderBySyncedAtDesc().map(Transmitter::syncedAt);
    }
}
