package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;
import java.util.List;

import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.xtce.ParameterValue;

public record ParameterValuesReceived(NoradId satellite, TelemetrySource source, Instant generationTime,
                                      List<ParameterValue> values) {

    public ParameterValuesReceived {
        values = List.copyOf(values);
    }
}
