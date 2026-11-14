package lt.vsh1ft.xtce;

import static lt.vsh1ft.xtce.XtceElements.NAME;
import static lt.vsh1ft.xtce.XtceElements.SHORT_DESCRIPTION;
import static lt.vsh1ft.xtce.XtceElements.attribute;
import static lt.vsh1ft.xtce.XtceElements.attributes;
import static lt.vsh1ft.xtce.XtceElements.invalid;
import static lt.vsh1ft.xtce.XtceElements.requireDefault;
import static lt.vsh1ft.xtce.XtceElements.unsupported;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Element;

final class ContainerTreeReader {

    private final Map<String, Parameter> parameters;
    private final Map<String, ContainerDefinition> containers = new LinkedHashMap<>();
    private final Set<String> built = new HashSet<>();

    ContainerTreeReader(Map<String, Parameter> parameters) {
        this.parameters = parameters;
    }

    void read(Element container) {
        attributes(container, NAME, SHORT_DESCRIPTION, "abstract");
        final var entries = new ArrayList<SequenceContainer.Entry>();
        String base = null;
        final var restriction = new ArrayList<PendingComparison>();
        for (var child : Xml.children(container)) {
            switch (child.getLocalName()) {
                case "EntryList" -> Xml.children(child).forEach(entry -> entries.add(readEntry(entry)));
                case "BaseContainer" -> {
                    attributes(child, "containerRef");
                    base = child.getAttribute("containerRef");
                    Xml.children(child).forEach(criteria -> restriction.addAll(readRestriction(criteria)));
                }
                default -> throw unsupported(child);
            }
        }
        final var name = container.getAttribute(NAME);
        containers.put(name, new ContainerDefinition(name, Boolean.parseBoolean(attribute(container, "abstract",
                "false")), entries, base, restriction));
    }

    SequenceContainer tree() {
        final var roots = containers.values().stream().filter(container -> container.base() == null).toList();
        if (roots.size() != 1) {
            throw new InvalidMissionDatabaseException("A mission database needs exactly one root container (one "
                    + "without a BaseContainer); found " + roots.stream().map(ContainerDefinition::name).toList());
        }
        containers.values().stream()
                .filter(container -> container.base() != null && !containers.containsKey(container.base()))
                .findFirst()
                .ifPresent(container -> {
                    throw new InvalidMissionDatabaseException("SequenceContainer '" + container.name()
                            + "' has unknown base container " + container.base());
                });
        final var root = build(roots.getFirst(), Set.of());
        if (built.size() < containers.size()) {
            throw new InvalidMissionDatabaseException("Containers in an inheritance cycle: "
                    + containers.keySet().stream().filter(name -> !built.contains(name)).toList());
        }
        return root;
    }

    private SequenceContainer.Entry readEntry(Element entry) {
        if (!entry.getLocalName().equals("ParameterRefEntry")) {
            throw unsupported(entry);
        }
        attributes(entry, "parameterRef", SHORT_DESCRIPTION);
        long offset = 0;
        for (var child : Xml.children(entry)) {
            if (!child.getLocalName().equals("LocationInContainerInBits")) {
                throw unsupported(child);
            }
            offset = readLocation(child);
        }
        return new SequenceContainer.Entry(parameter(entry), offset);
    }

    private static long readLocation(Element location) {
        attributes(location, "referenceLocation");
        requireDefault(location, "referenceLocation", "previousEntry");
        final var value = Xml.children(location).getFirst();
        if (!value.getLocalName().equals("FixedValue")) {
            throw unsupported(value);
        }
        final long offset = Long.parseLong(value.getTextContent().strip());
        if (offset < 0) {
            throw invalid(location, "is " + offset + " bits; an entry can't overlap the one before it");
        }
        return offset;
    }

    private List<PendingComparison> readRestriction(Element criteria) {
        final var condition = Xml.children(criteria).getFirst();
        return switch (condition.getLocalName()) {
            case "Comparison" -> List.of(readComparison(condition));
            case "ComparisonList" -> Xml.children(condition).stream().map(this::readComparison).toList();
            default -> throw unsupported(condition);
        };
    }

    private PendingComparison readComparison(Element comparison) {
        attributes(comparison, "parameterRef", "value", "comparisonOperator", "useCalibratedValue", "instance");
        requireDefault(comparison, "comparisonOperator", "==");
        requireDefault(comparison, "instance", "0");
        return new PendingComparison(parameter(comparison), comparison.getAttribute("value"),
                Boolean.parseBoolean(attribute(comparison, "useCalibratedValue", "true")));
    }

    private Parameter parameter(Element reference) {
        final var name = reference.getAttribute("parameterRef");
        final var parameter = parameters.get(name);
        if (parameter == null) {
            throw invalid(reference, "refers to unknown parameter " + name);
        }
        return parameter;
    }

    private SequenceContainer build(ContainerDefinition definition, Set<String> decodedBefore) {
        built.add(definition.name());
        final var decoded = new HashSet<>(decodedBefore);
        definition.entries().forEach(entry -> decoded.add(entry.parameter().name()));
        final var children = containers.values().stream()
                .filter(child -> definition.name().equals(child.base()))
                .map(child -> new SequenceContainer.Child(build(child, decoded), restriction(child, decoded)))
                .toList();
        return new SequenceContainer(definition.name(), definition.isAbstract(), definition.entries(), children);
    }

    private static List<Comparison> restriction(ContainerDefinition container, Set<String> decoded) {
        return container.restriction().stream().map(pending -> comparison(container, pending, decoded)).toList();
    }

    private static Comparison comparison(ContainerDefinition container, PendingComparison pending,
                                         Set<String> decoded) {
        final var parameter = pending.parameter();
        if (!decoded.contains(parameter.name())) {
            throw new InvalidMissionDatabaseException("The restriction of SequenceContainer '"
                    + container.name() + "' compares " + parameter.name()
                    + ", which is not decoded before it");
        }
        try {
            final EngineeringValue expected = pending.calibrated()
                    ? parameter.type().parse(pending.value())
                    : new IntegerValue(Long.parseLong(pending.value()));
            return new Comparison(parameter, expected, pending.calibrated());
        } catch (IllegalArgumentException e) {
            throw new InvalidMissionDatabaseException("The restriction of SequenceContainer '"
                    + container.name() + "' compares " + parameter.name() + " with '" + pending.value()
                    + "', which is not a valid value: " + e.getMessage());
        }
    }

    private record PendingComparison(Parameter parameter, String value, boolean calibrated) {
    }

    private record ContainerDefinition(String name, boolean isAbstract, List<SequenceContainer.Entry> entries,
                                       String base, List<PendingComparison> restriction) {
    }
}
