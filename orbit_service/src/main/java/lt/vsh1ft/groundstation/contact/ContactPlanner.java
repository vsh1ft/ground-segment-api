package lt.vsh1ft.groundstation.contact;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.groundstation.elements.OrbitDataUnavailableException;
import lt.vsh1ft.groundstation.station.GroundStationSource;
import lt.vsh1ft.groundstation.pass.PassPredictor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class ContactPlanner {

    static final Duration LOOK_BACK = Duration.ofHours(1);
    static final Duration LOOK_AHEAD = Duration.ofHours(24);

    private final GroundStationSource stations;
    private final PassPredictor predictor;

    ContactPlan plan(NoradId satellite, String stationName, Instant now) {
        final var station = Optional.ofNullable(stationName).flatMap(stations::byName);
        if (station.isEmpty()) {
            log.warn("No station named {}; packets of satellite {} have UNKNOWN visibility", stationName, satellite);
            return ContactPlan.UNKNOWN;
        }
        try {
            final var forecast = predictor.predict(satellite, station.get(), now.minus(LOOK_BACK),
                    LOOK_BACK.plus(LOOK_AHEAD));
            return ContactPlan.of(forecast.passes());
        } catch (OrbitDataUnavailableException e) {
            log.warn("{}; packets of satellite {} have UNKNOWN visibility", e.getMessage(), satellite);
            return ContactPlan.UNKNOWN;
        }
    }
}
