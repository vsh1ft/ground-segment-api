package lt.vsh1ft.groundstation.station;

import java.util.Optional;
import java.util.UUID;

public interface GroundStationSource {

    GroundStation byId(UUID id);

    Optional<GroundStation> byName(String name);
}
