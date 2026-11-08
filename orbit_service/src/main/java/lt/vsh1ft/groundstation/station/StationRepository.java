package lt.vsh1ft.groundstation.station;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.ListCrudRepository;

public interface StationRepository extends ListCrudRepository<Station, UUID> {

    List<Station> findAllByOrderByName();

    Optional<Station> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);
}
