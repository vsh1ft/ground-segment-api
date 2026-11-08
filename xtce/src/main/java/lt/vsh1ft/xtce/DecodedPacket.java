package lt.vsh1ft.xtce;

import java.util.List;

public record DecodedPacket(SequenceContainer container, List<ParameterValue> values) {

    public DecodedPacket {
        values = List.copyOf(values);
    }

    public boolean complete() {
        return !container.isAbstract();
    }
}
