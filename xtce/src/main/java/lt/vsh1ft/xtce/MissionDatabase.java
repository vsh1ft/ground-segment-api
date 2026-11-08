package lt.vsh1ft.xtce;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

public record MissionDatabase(String name, List<Parameter> parameters, SequenceContainer root) {

    public MissionDatabase {
        parameters = List.copyOf(parameters);
    }

    public Optional<Parameter> parameter(String name) {
        return parameters.stream().filter(parameter -> parameter.name().equals(name)).findFirst();
    }

    public DecodedPacket decode(byte[] packet) {
        final var reader = new BitReader(packet);
        final var decoded = new LinkedHashMap<String, ParameterValue>();
        var container = root;
        while (true) {
            for (var entry : container.entries()) {
                decoded.put(entry.name(), entry.decode(reader));
            }
            final var matching = container.children().stream().filter(child -> child.matches(decoded)).toList();
            if (matching.isEmpty()) {
                return new DecodedPacket(container, List.copyOf(decoded.values()));
            }
            if (matching.size() > 1) {
                throw new PacketDecodingException("Packet matches several containers based on " + container.name()
                        + ": " + matching.stream().map(child -> child.container().name()).toList());
            }
            container = matching.getFirst().container();
        }
    }
}
