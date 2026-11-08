package lt.vsh1ft.xtce;

import java.io.ByteArrayInputStream;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

final class XtceSchema {

    private static final Schema SCHEMA = Xml.unchecked(XtceSchema::load);

    private XtceSchema() {
    }

    static void validate(byte[] xtce) {
        Xml.unchecked(() -> {
            final var validator = SCHEMA.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.validate(new StreamSource(new ByteArrayInputStream(xtce)));
            return null;
        });
    }

    private static Schema load() throws Exception {
        final var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar");
        return factory.newSchema(new Source[] {source("xml.xsd"), source("SpaceSystem.xsd")});
    }

    private static Source source(String resource) {
        return new StreamSource(XtceSchema.class.getResource(resource).toExternalForm());
    }
}
