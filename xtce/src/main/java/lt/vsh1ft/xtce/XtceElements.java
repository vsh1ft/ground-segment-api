package lt.vsh1ft.xtce;

import java.util.Set;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

final class XtceElements {

    static final String NAME = "name";
    static final String SHORT_DESCRIPTION = "shortDescription";

    private XtceElements() {
    }

    static void attributes(Element element, String... supported) {
        final var allowed = Set.of(supported);
        final var attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            final var attribute = (Attr) attributes.item(i);
            if (attribute.getNamespaceURI() == null && !allowed.contains(attribute.getLocalName())) {
                throw invalid(element, "has attribute " + attribute.getLocalName() + ", which is not supported");
            }
        }
    }

    static void requireDefault(Element element, String attribute, String required) {
        if (!attribute(element, attribute, required).equals(required)) {
            throw invalid(element, "has " + attribute + "=\"" + element.getAttribute(attribute) + "\"; only \""
                    + required + "\" is supported");
        }
    }

    static String attribute(Element element, String name, String defaultValue) {
        return element.hasAttribute(name) ? element.getAttribute(name) : defaultValue;
    }

    static InvalidMissionDatabaseException unsupported(Element element) {
        return new InvalidMissionDatabaseException("Unsupported XTCE element " + describe(element));
    }

    static InvalidMissionDatabaseException invalid(Element element, String problem) {
        return new InvalidMissionDatabaseException(describe(element) + " " + problem);
    }

    private static String describe(Element element) {
        if (element.hasAttribute(NAME)) {
            return element.getLocalName() + " '" + element.getAttribute(NAME) + "'";
        }
        return element.getLocalName() + " in " + describe((Element) element.getParentNode());
    }
}
