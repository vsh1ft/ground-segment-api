package lt.vsh1ft.groundstation.pass;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Stateful: one instance per prediction. Passes cut off by the start or end of the window are dropped. */
public final class PassAssembler {

    private final List<Pass> passes = new ArrayList<>();
    private Instant start;
    private Pass.Peak peak;

    public void onAcquisition(Instant time) {
        start = time;
        peak = null;
    }

    public void onMaximum(Instant time, double elevationDeg) {
        if (!isInPass()) {
            return;
        }
        if (peak == null || elevationDeg > peak.elevationDeg()) {
            peak = new Pass.Peak(time, elevationDeg);
        }
    }

    public void onLoss(Instant time) {
        if (isInPass() && peak != null) {
            passes.add(new Pass(start, time, peak));
        }
        start = null;
    }

    public List<Pass> passes() {
        return List.copyOf(passes);
    }

    private boolean isInPass() {
        return start != null;
    }
}
