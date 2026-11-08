package lt.vsh1ft.groundstation.tmtc;

import java.util.Map;
import java.util.TreeMap;

import io.swagger.v3.oas.annotations.media.Schema;
import lt.vsh1ft.xtce.AbsoluteTimeParameterType;
import lt.vsh1ft.xtce.EnumeratedParameterType;
import lt.vsh1ft.xtce.FloatParameterType;
import lt.vsh1ft.xtce.IntegerParameterType;
import lt.vsh1ft.xtce.Parameter;
import lt.vsh1ft.xtce.StringParameterType;

@Schema(description = "A telemetry parameter defined in the satellite's mission database")
public record ParameterResponse(
        @Schema(description = "Parameter name, unique within the mission database", example = "BATTERY_VOLTAGE")
        String name,

        @Schema(description = "Short description from the mission database", example = "Battery voltage",
                nullable = true)
        String description,

        @Schema(description = "Kind of engineering value", example = "FLOAT")
        Kind kind,

        @Schema(description = "Unit of the engineering value", example = "V", nullable = true)
        String unit,

        @Schema(description = "For ENUMERATED parameters: raw value to label", example = "{\"0\": \"SAFE\", "
                + "\"1\": \"NOMINAL\"}", nullable = true)
        Map<Long, String> labels) {

    static ParameterResponse from(Parameter parameter) {
        return switch (parameter.type()) {
            case IntegerParameterType type ->
                    new ParameterResponse(parameter.name(), parameter.description(), Kind.INTEGER, type.unit(), null);
            case FloatParameterType type ->
                    new ParameterResponse(parameter.name(), parameter.description(), Kind.FLOAT, type.unit(), null);
            case EnumeratedParameterType type -> new ParameterResponse(parameter.name(), parameter.description(),
                    Kind.ENUMERATED, null, new TreeMap<>(type.labels()));
            case StringParameterType type ->
                    new ParameterResponse(parameter.name(), parameter.description(), Kind.STRING, null, null);
            case AbsoluteTimeParameterType type ->
                    new ParameterResponse(parameter.name(), parameter.description(), Kind.TIME, null, null);
        };
    }

    @Schema(description = "INTEGER: whole numbers; FLOAT: calibrated numbers; ENUMERATED: labels; STRING: text; "
            + "TIME: absolute times in UTC")
    public enum Kind {
        INTEGER, FLOAT, ENUMERATED, STRING, TIME
    }
}
