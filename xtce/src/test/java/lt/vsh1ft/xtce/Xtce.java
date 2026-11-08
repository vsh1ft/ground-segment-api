package lt.vsh1ft.xtce;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

final class Xtce {

    static final String UINT8 = """
            <IntegerParameterType name="uint8_t" signed="false"><IntegerDataEncoding/></IntegerParameterType>""";
    static final String ID = """
            <Parameter name="ID" parameterTypeRef="uint8_t"/>""";
    static final String ROOT = """
            <SequenceContainer name="ROOT" abstract="true">
              <EntryList><ParameterRefEntry parameterRef="ID"/></EntryList>
            </SequenceContainer>""";

    private Xtce() {
    }

    static MissionDatabase housekeeping() {
        try (var xml = Xtce.class.getResourceAsStream("housekeeping.xml")) {
            return XtceReader.read(xml);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static MissionDatabase read(String xml) {
        return XtceReader.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    static MissionDatabase read(String types, String parameters, String containers) {
        return read(spaceSystem("<TelemetryMetaData><ParameterTypeSet>" + types + "</ParameterTypeSet><ParameterSet>"
                + parameters + "</ParameterSet><ContainerSet>" + containers + "</ContainerSet></TelemetryMetaData>"));
    }

    static String spaceSystem(String content) {
        return "<SpaceSystem xmlns=\"http://www.omg.org/spec/XTCE/20180204\" name=\"TEST\">" + content
                + "</SpaceSystem>";
    }

    static byte[] bytes(String hex) {
        return HexFormat.of().parseHex(hex.replace(" ", ""));
    }
}
