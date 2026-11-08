package lt.vsh1ft.xtce;

import java.util.List;
import java.util.Map;

public record SequenceContainer(String name, boolean isAbstract, List<Parameter> entries, List<Child> children) {

    public SequenceContainer {
        entries = List.copyOf(entries);
        children = List.copyOf(children);
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
