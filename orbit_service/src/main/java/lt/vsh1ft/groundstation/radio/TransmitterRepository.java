package lt.vsh1ft.groundstation.radio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;

interface TransmitterRepository extends ListCrudRepository<Transmitter, String> {

    List<Transmitter> findByNoradIdOrderByDownlinkLowHzAscDescriptionAsc(int noradId);

    List<Transmitter> findByNoradIdAndStatusOrderByDownlinkLowHzAscDescriptionAsc(int noradId, TransmitterStatus status);

    Optional<Transmitter> findFirstByOrderBySyncedAtDesc();
}
