package lt.vsh1ft.groundstation.pass;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class PassAssembler {

    private final List<Pass> passes = new ArrayList<>();
    private Pass.Crossing start;
    private Pass.Peak peak;

    public void onAcquisition(Instant time, double azimuthDeg) {
        start = new Pass.Crossing(time, azimuthDeg);
        peak = null;
    }

    public void onMaximum(Instant time, double elevationDeg, double azimuthDeg) {
        if (!isInPass()) {
            return;
        }
        if (peak == null || elevationDeg > peak.elevationDeg()) {
            peak = new Pass.Peak(time, elevationDeg, azimuthDeg);
        }
    }

    public void onLoss(Instant time, double azimuthDeg) {
        if (isInPass() && peak != null) {
            passes.add(new Pass(start, new Pass.Crossing(time, azimuthDeg), peak));
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
