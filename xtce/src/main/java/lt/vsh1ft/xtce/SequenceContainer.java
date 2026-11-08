package lt.vsh1ft.xtce;

import java.util.List;
import java.util.Map;

public record SequenceContainer(String name, boolean isAbstract, List<Entry> entries, List<Child> children) {

    public SequenceContainer {
        entries = List.copyOf(entries);
        children = List.copyOf(children);
    }

    public List<Parameter> parameters() {
        return entries.stream().map(Entry::parameter).toList();
    }

    public record Entry(Parameter parameter, long offsetInBits) {

        public Entry {
            if (offsetInBits < 0) {
                throw new IllegalArgumentException("an entry can't overlap the one before it, offset was "
                        + offsetInBits);
            }
        }

        public Entry(Parameter parameter) {
            this(parameter, 0);
        }

        ParameterValue decode(BitReader reader) {
            reader.skip(offsetInBits);
            return parameter.decode(reader);
        }
    }

    public record Child(SequenceContainer container, List<Comparison> restriction) {

        public Child {
            restriction = List.copyOf(restriction);
        }

        boolean matches(Map<String, ParameterValue> decoded) {
            return restriction.stream().allMatch(comparison -> comparison.matches(decoded));
        }
    }
}
