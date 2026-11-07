package lt.vsh1ft.groundstation.elements;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.orekit.propagation.analytical.tle.TLE;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoredTleSource implements TleSource {

    private final StoredOmmRepository repository;

    @Override
    public TLE latest(NoradId satellite) {
        return repository.findFirstByNoradIdOrderByEpochDesc(satellite.value())
                .map(OmmTleConverter::toTle)
                .orElseThrow(() -> new OrbitDataUnavailableException("No orbit data stored for NORAD ID " + satellite));
    }
}
