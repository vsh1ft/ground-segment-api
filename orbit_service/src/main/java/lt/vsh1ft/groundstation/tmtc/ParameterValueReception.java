package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.xtce.ParameterValue;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ParameterValueReception {

    private final ParameterArchive archive;
    private final ApplicationEventPublisher publisher;

    public void receive(NoradId satellite, TelemetrySource source, Instant generationTime,
                        List<ParameterValue> values) {
        archive.store(satellite, source, generationTime, values);
        publisher.publishEvent(new ParameterValuesReceived(satellite, source, generationTime, values));
    }
}
