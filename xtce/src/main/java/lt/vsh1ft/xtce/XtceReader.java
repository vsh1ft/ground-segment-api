package lt.vsh1ft.xtce;

import static lt.vsh1ft.xtce.XtceElements.NAME;
import static lt.vsh1ft.xtce.XtceElements.SHORT_DESCRIPTION;
import static lt.vsh1ft.xtce.XtceElements.attribute;
import static lt.vsh1ft.xtce.XtceElements.attributes;
import static lt.vsh1ft.xtce.XtceElements.invalid;
import static lt.vsh1ft.xtce.XtceElements.unsupported;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;

public final class XtceReader {

    private final Map<String, ParameterType> types = new LinkedHashMap<>();
    private final Map<String, Parameter> parameters = new LinkedHashMap<>();
    private final ContainerTreeReader containers = new ContainerTreeReader(parameters);

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
        return new MissionDatabase(spaceSystem.getAttribute(NAME), List.copyOf(parameters.values()),
                containers.tree());
    }

    private void readTelemetry(Element telemetry) {
        for (var set : Xml.children(telemetry)) {
            switch (set.getLocalName()) {
                case "ParameterTypeSet" -> Xml.children(set).forEach(this::readType);
                case "ParameterSet" -> Xml.children(set).forEach(this::readParameter);
                case "ContainerSet" -> Xml.children(set).forEach(containers::read);
                default -> throw unsupported(set);
            }
        }
    }

    private void readType(Element type) {
        types.put(type.getAttribute(NAME), ParameterTypeReader.read(type));
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
        parameters.put(name, new Parameter(name, attribute(element, SHORT_DESCRIPTION, null), type));
    }
}
