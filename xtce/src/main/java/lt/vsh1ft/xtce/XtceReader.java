package lt.vsh1ft.xtce;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public final class XtceReader {

    private static final String NAME = "name";
    private static final String SHORT_DESCRIPTION = "shortDescription";

    private final Map<String, ParameterType> types = new LinkedHashMap<>();
    private final Map<String, Parameter> parameters = new LinkedHashMap<>();
    private final Map<String, ContainerDefinition> containers = new LinkedHashMap<>();
    private final Set<String> built = new HashSet<>();

    private XtceReader() {
    }

    public static MissionDatabase read(InputStream xtce) {
        final byte[] bytes = Xml.unchecked(xtce::readAllBytes);
        final var document = Xml.parse(bytes);
        XtceSchema.validate(bytes);
        return new XtceReader().read(document.getDocumentElement());
    }

    private MissionDatabase read(Element spaceSystem) {
        attributes(spaceSystem, NAME, SHORT_DESCRIPTION);
        for (var child : Xml.children(spaceSystem)) {
            switch (child.getLocalName()) {
                case "Header" -> {
                }
                case "TelemetryMetaData" -> readTelemetry(child);
                default -> throw unsupported(child);
            }
        }
        return new MissionDatabase(spaceSystem.getAttribute(NAME), List.copyOf(parameters.values()), buildTree());
    }

    private void readTelemetry(Element telemetry) {
        for (var set : Xml.children(telemetry)) {
            switch (set.getLocalName()) {
                case "ParameterTypeSet" -> Xml.children(set).forEach(this::readType);
                case "ParameterSet" -> Xml.children(set).forEach(this::readParameter);
                case "ContainerSet" -> Xml.children(set).forEach(this::readContainer);
                default -> throw unsupported(set);
            }
        }
    }

    private void readType(Element type) {
        final var kind = type.getLocalName();
        switch (kind) {
            case "IntegerParameterType" -> attributes(type, NAME, SHORT_DESCRIPTION, "signed", "sizeInBits");
            case "FloatParameterType" -> attributes(type, NAME, SHORT_DESCRIPTION, "sizeInBits");
            case "EnumeratedParameterType" -> attributes(type, NAME, SHORT_DESCRIPTION);
            default -> throw unsupported(type);
        }
        final boolean calibrated = kind.equals("FloatParameterType");
        String unit = null;
        Encoding encoding = null;
        Enumeration enumeration = new Enumeration(Map.of(), Map.of());
        for (var child : Xml.children(type)) {
            switch (child.getLocalName()) {
                case "UnitSet" -> unit = readUnit(child);
                case "IntegerDataEncoding" -> encoding = readEncoding(child, calibrated);
                case "EnumerationList" -> enumeration = readEnumeration(child);
                default -> throw unsupported(child);
            }
        }
        if (encoding == null) {
            throw invalid(type, "has no IntegerDataEncoding");
        }
        final var name = type.getAttribute(NAME);
        final ParameterType parameterType;
        if (kind.equals("IntegerParameterType")) {
            parameterType = new IntegerParameterType(name, unit, encoding.encoding());
        } else if (calibrated) {
            parameterType = new FloatParameterType(name, unit, encoding.encoding(), encoding.calibrator());
        } else {
            parameterType = new EnumeratedParameterType(name, encoding.encoding(), enumeration.labels(),
                    enumeration.descriptions());
        }
        types.put(name, parameterType);
    }

    private String readUnit(Element unitSet) {
        final var units = Xml.children(unitSet);
        if (units.size() > 1) {
            throw invalid(unitSet, "has more than one Unit, which is not supported");
        }
        units.forEach(unit -> attributes(unit));
        return units.isEmpty() ? null : units.getFirst().getTextContent().strip();
    }

    private Encoding readEncoding(Element encoding, boolean calibrated) {
        attributes(encoding, "encoding", "sizeInBits", "byteOrder", "bitOrder");
        final boolean signed = switch (attribute(encoding, "encoding", "unsigned")) {
            case "unsigned" -> false;
            case "twosComplement" -> true;
            default -> throw invalid(encoding, "uses encoding " + encoding.getAttribute("encoding")
                    + "; only unsigned and twosComplement are supported");
        };
        final long sizeInBits = Long.parseLong(attribute(encoding, "sizeInBits", "8"));
        if (sizeInBits > IntegerEncoding.MAX_SIZE_IN_BITS) {
            throw invalid(encoding, "is " + sizeInBits + " bits; at most " + IntegerEncoding.MAX_SIZE_IN_BITS
                    + " are supported");
        }
        requireDefault(encoding, "byteOrder", "mostSignificantByteFirst");
        requireDefault(encoding, "bitOrder", "mostSignificantBitFirst");
        var calibrator = PolynomialCalibrator.IDENTITY;
        for (var child : Xml.children(encoding)) {
            if (!calibrated || !child.getLocalName().equals("DefaultCalibrator")) {
                throw unsupported(child);
            }
            calibrator = readCalibrator(child);
        }
        return new Encoding(new IntegerEncoding((int) sizeInBits, signed), calibrator);
    }

    private PolynomialCalibrator readCalibrator(Element defaultCalibrator) {
        attributes(defaultCalibrator, NAME, SHORT_DESCRIPTION);
        final var calibrator = Xml.children(defaultCalibrator).getFirst();
        if (!calibrator.getLocalName().equals("PolynomialCalibrator")) {
            throw unsupported(calibrator);
        }
        attributes(calibrator, NAME, SHORT_DESCRIPTION);
        final var terms = new ArrayList<PolynomialCalibrator.Term>();
        for (var term : Xml.children(calibrator)) {
            attributes(term, "coefficient", "exponent");
            terms.add(new PolynomialCalibrator.Term(Integer.parseInt(term.getAttribute("exponent")),
                    Double.parseDouble(term.getAttribute("coefficient"))));
        }
        return new PolynomialCalibrator(terms);
    }

    private Enumeration readEnumeration(Element enumerationList) {
        final var labels = new LinkedHashMap<Long, String>();
        final var descriptions = new LinkedHashMap<Long, String>();
        for (var enumeration : Xml.children(enumerationList)) {
            attributes(enumeration, "value", "label", SHORT_DESCRIPTION);
            final long value = Long.parseLong(enumeration.getAttribute("value"));
            if (labels.putIfAbsent(value, enumeration.getAttribute("label")) != null) {
                throw invalid(enumerationList, "has value " + value + " more than once");
            }
            if (enumeration.hasAttribute(SHORT_DESCRIPTION)) {
                descriptions.put(value, enumeration.getAttribute(SHORT_DESCRIPTION));
            }
        }
        return new Enumeration(labels, descriptions);
    }

    private void readParameter(Element element) {
        if (!element.getLocalName().equals("Parameter")) {
            throw unsupported(element);
        }
        attributes(element, NAME, SHORT_DESCRIPTION, "parameterTypeRef");
        Xml.children(element).stream().findFirst().ifPresent(child -> {
            throw unsupported(child);
        });
        final var typeRef = element.getAttribute("parameterTypeRef");
        final var type = types.get(typeRef);
        if (type == null) {
            throw invalid(element, "refers to unknown parameter type " + typeRef);
        }
        final var name = element.getAttribute(NAME);
        final var description = attribute(element, SHORT_DESCRIPTION, null);
        parameters.put(name, new Parameter(name, description, type));
    }

    private void readContainer(Element container) {
        attributes(container, NAME, SHORT_DESCRIPTION, "abstract");
        final var entries = new ArrayList<Parameter>();
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

    private Parameter readEntry(Element entry) {
        if (!entry.getLocalName().equals("ParameterRefEntry")) {
            throw unsupported(entry);
        }
        attributes(entry, "parameterRef", SHORT_DESCRIPTION);
        Xml.children(entry).stream().findFirst().ifPresent(child -> {
            throw unsupported(child);
        });
        return parameter(entry);
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

    private SequenceContainer buildTree() {
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

    private SequenceContainer build(ContainerDefinition definition, Set<String> decodedBefore) {
        built.add(definition.name());
        final var decoded = new HashSet<>(decodedBefore);
        definition.entries().forEach(entry -> decoded.add(entry.name()));
        final var children = containers.values().stream()
                .filter(child -> definition.name().equals(child.base()))
                .map(child -> new SequenceContainer.Child(build(child, decoded), restriction(child, decoded)))
                .toList();
        return new SequenceContainer(definition.name(), definition.isAbstract(), definition.entries(), children);
    }

    private static List<Comparison> restriction(ContainerDefinition container, Set<String> decoded) {
        return container.restriction().stream().map(pending -> {
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
        }).toList();
    }

    private static void attributes(Element element, String... supported) {
        final var allowed = Set.of(supported);
        final var attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            final var attribute = (Attr) attributes.item(i);
            if (attribute.getNamespaceURI() == null && !allowed.contains(attribute.getLocalName())) {
                throw invalid(element, "has attribute " + attribute.getLocalName() + ", which is not supported");
            }
        }
    }

    private static void requireDefault(Element element, String attribute, String required) {
        if (!attribute(element, attribute, required).equals(required)) {
            throw invalid(element, "has " + attribute + "=\"" + element.getAttribute(attribute) + "\"; only \""
                    + required + "\" is supported");
        }
    }

    private static String attribute(Element element, String name, String defaultValue) {
        return element.hasAttribute(name) ? element.getAttribute(name) : defaultValue;
    }

    private static InvalidMissionDatabaseException unsupported(Element element) {
        return new InvalidMissionDatabaseException("Unsupported XTCE element " + describe(element));
    }

    private static InvalidMissionDatabaseException invalid(Element element, String problem) {
        return new InvalidMissionDatabaseException(describe(element) + " " + problem);
    }

    private static String describe(Element element) {
        if (element.hasAttribute(NAME)) {
            return element.getLocalName() + " '" + element.getAttribute(NAME) + "'";
        }
        return element.getLocalName() + " in " + describe((Element) element.getParentNode());
    }

    private record Encoding(IntegerEncoding encoding, PolynomialCalibrator calibrator) {
    }

    private record Enumeration(Map<Long, String> labels, Map<Long, String> descriptions) {
    }

    private record PendingComparison(Parameter parameter, String value, boolean calibrated) {
    }

    private record ContainerDefinition(String name, boolean isAbstract, List<Parameter> entries, String base,
                                       List<PendingComparison> restriction) {
    }
}
