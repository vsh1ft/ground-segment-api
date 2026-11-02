package lt.vsh1ft.groundstation.pass;

import java.util.List;

public interface PassReporter {

    void report(GroundStation station, List<Pass> passes);
}
