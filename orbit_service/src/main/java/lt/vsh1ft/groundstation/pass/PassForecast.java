package lt.vsh1ft.groundstation.pass;

import java.time.Instant;
import java.util.List;

public record PassForecast(Instant elementsEpoch, List<Pass> passes) {
}
