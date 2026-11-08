package lt.vsh1ft.groundstation.station;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.pass.GroundStation;
import lt.vsh1ft.groundstation.pass.GroundStationSource;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StationService implements GroundStationSource {

    private final StationRepository repository;
    private final JdbcAggregateOperations aggregates;

    public List<Station> all() {
        return repository.findAllByOrderByName();
    }

    public Station get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new StationNotFoundException(id));
    }

    @Override
    public GroundStation byId(UUID id) {
        return get(id).toGroundStation();
    }

    @Override
    public Optional<GroundStation> byName(String name) {
        return repository.findByName(name).map(Station::toGroundStation);
    }

    @Transactional
    public Station create(GroundStation station) {
        if (repository.existsByName(station.name())) {
            throw new StationNameTakenException(station.name());
        }
        return aggregates.insert(Station.of(UUID.randomUUID(), station));
    }

    @Transactional
    public void update(UUID id, GroundStation station) {
        if (!repository.existsById(id)) {
            throw new StationNotFoundException(id);
        }
        if (repository.existsByNameAndIdNot(station.name(), id)) {
            throw new StationNameTakenException(station.name());
        }
        repository.save(Station.of(id, station));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new StationNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
