package lt.vsh1ft.xtce;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.helpers.DefaultHandler;

final class Xml {

    private static final Set<String> DOCUMENTATION = Set.of("LongDescription", "AliasSet", "AncillaryDataSet");

    private Xml() {
    }

    static Document parse(byte[] xml) {
        return unchecked(() -> {
            final var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            final var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            return builder.parse(new ByteArrayInputStream(xml));
        });
    }

    static List<Element> children(Element parent) {
        final var children = new ArrayList<Element>();
        for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child && !DOCUMENTATION.contains(child.getLocalName())) {
                children.add(child);
            }
        }
        return children;
    }

    static <T> T unchecked(Action<T> action) {
        try {
            return action.run();
        } catch (Exception e) {
            throw new InvalidMissionDatabaseException("Not a valid XTCE 1.2 document: " + e.getMessage());
        }
    }

    @FunctionalInterface
    interface Action<T> {

        T run() throws Exception;
    }
}
