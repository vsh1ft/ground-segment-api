package lt.vsh1ft.groundstation.elements;

import lt.vsh1ft.groundstation.shared.NoradId;
import org.orekit.propagation.analytical.tle.TLE;

public interface TleSource {

    TLE latest(NoradId satellite);
}
