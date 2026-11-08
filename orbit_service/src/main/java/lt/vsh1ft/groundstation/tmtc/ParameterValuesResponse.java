package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.swagger.v3.oas.annotations.media.Schema;
import lt.vsh1ft.xtce.EnumeratedParameterType;
import lt.vsh1ft.xtce.FloatParameterType;
import lt.vsh1ft.xtce.IntegerParameterType;
import lt.vsh1ft.xtce.Parameter;

@Schema(description = "Archived values of one parameter of a satellite, most recently archived first")
public record ParameterValuesResponse(
        @Schema(description = "Parameter name", example = "BATTERY_VOLTAGE")
        String parameter,

        @Schema(description = "Unit of the engineering values", example = "V", nullable = true)
        String unit,

        @Schema(description = "The values, most recently archived first")
        List<Value> values,

        @Schema(description = "Pass as `before` to get the next, older page; null when there are no more values",
                example = "4692", nullable = true)
        Long nextBefore) {

    static ParameterValuesResponse of(Parameter parameter, List<StoredParameterValue> values, Long nextBefore) {
        return new ParameterValuesResponse(parameter.name(), parameter.type().unit(),
                values.stream().map(value -> Value.of(parameter, value)).toList(), nextBefore);
    }

    @Schema(description = "One value of the parameter, decoded from one packet")
    public record Value(
            @Schema(description = "Archive identifier; higher means archived later", example = "4711")
            long id,

            @Schema(description = "When the spacecraft measured the value, from its on-board time, UTC",
                    example = "2026-11-08T08:53:41.923157Z")
            Instant generationTime,

            @Schema(description = "The value as sent by the spacecraft, before calibration", example = "7411")
            long raw,

            @Schema(description = "The calibrated value: a number for INTEGER and FLOAT parameters, a label for "
                    + "ENUMERATED ones; null when the raw value has no enumeration label",
                    example = "7.411", nullable = true, oneOf = {Long.class, Double.class, String.class})
            Object engineering,

            @Schema(description = "Archive identifier of the packet the value was decoded from", example = "12")
            long packetId) {

        static Value of(Parameter parameter, StoredParameterValue value) {
            return new Value(value.id(), value.generationTime(), value.raw(), engineering(parameter, value),
                    value.packetId());
        }

        private static Object engineering(Parameter parameter, StoredParameterValue value) {
            final var number = Optional.ofNullable(value.engineeringNumber());
            return switch (parameter.type()) {
                case IntegerParameterType type -> number.map(Double::longValue).orElse(null);
                case FloatParameterType type -> number.orElse(null);
                case EnumeratedParameterType type -> value.engineeringText();
            };
        }
    }
}
