package lt.vsh1ft.groundstation.tle;

import lt.vsh1ft.groundstation.pass.NoradId;
import org.orekit.propagation.analytical.tle.TLE;

public interface TleSource {

    TLE latest(NoradId satellite);
}
