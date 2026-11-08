package lt.vsh1ft.xtce;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public record DecodedPacket(SequenceContainer container, List<ParameterValue> values) {

    public DecodedPacket {
        values = List.copyOf(values);
    }

    public Optional<Instant> generationTime() {
        return values.stream()
                .flatMap(value -> value.engineering().stream())
                .flatMap(engineering -> engineering instanceof TimeValue(Instant time) ? Stream.of(time) : Stream.empty())
                .findFirst();
    }

    public boolean complete() {
        return !container.isAbstract();
    }
}
