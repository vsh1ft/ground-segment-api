package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One page of a satellite's event log, most recently archived first")
public record EventsResponse(
        @Schema(description = "The events, most recently archived first")
        List<Event> events,

        @Schema(description = "Pass as `before` to get the next, older page; null when there are no more events",
                example = "4692", nullable = true)
        Long nextBefore) {

    @Schema(description = "An event reported by the spacecraft (PUS service 5)")
    public record Event(
            @Schema(description = "Archive identifier; higher means archived later", example = "7")
            long id,

            @Schema(description = "Severity assigned by the spacecraft", example = "LOW")
            Severity severity,

            @Schema(description = "Event definition ID as sent; null if the packet could not be decoded",
                    example = "1", nullable = true)
            Integer eventId,

            @Schema(description = "Event name from the mission database; null if the database doesn't know the ID",
                    example = "BATTERY_LOW", nullable = true)
            String name,

            @Schema(description = "Event text from the mission database", example = "Battery voltage below 7.15 V",
                    nullable = true)
            String description,

            @Schema(description = "When the spacecraft generated the report, UTC", example = "2026-11-08T09:00:00Z")
            Instant generationTime,

            @Schema(description = "Archive identifier of the event's packet", example = "812")
            long packetId,

            @Schema(description = "Auxiliary values of the event, as defined by its container in the mission "
                    + "database")
            List<Value> values) {
    }

    @Schema(description = "An auxiliary value of an event")
    public record Value(
            @Schema(description = "Parameter name", example = "EVENT_BATTERY_VOLTAGE")
            String parameter,

            @Schema(description = "The value as sent, before calibration", example = "7149")
            long raw,

            @Schema(description = "The calibrated value: a number, or a label for enumerations", example = "7.149",
                    nullable = true, oneOf = {Long.class, Double.class, String.class})
            Object engineering) {
    }
}
