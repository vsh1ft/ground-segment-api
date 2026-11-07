package lt.vsh1ft.groundstation.radio;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A radio transmitter, transceiver or transponder on a satellite, as listed in the SatNOGS DB")
public record TransmitterResponse(
        @Schema(description = "SatNOGS DB identifier", example = "ZJxCeQmih9zDfYNVrB4wRN")
        String uuid,

        @Schema(description = "What the transmitter is used for", example = "Mode V APRS")
        String description,

        @Schema(description = "Transmitter (downlink only), transceiver (uplink and downlink) or transponder "
                + "(a frequency band relayed from uplink to downlink)", example = "TRANSCEIVER")
        TransmitterType type,

        @Schema(description = "Whether it is on the air", example = "ACTIVE")
        TransmitterStatus status,

        @Schema(description = "Uplink frequency, or the lower edge of the uplink band, Hz", example = "145825000")
        Long uplinkLowHz,

        @Schema(description = "Upper edge of the uplink band, Hz; transponders only")
        Long uplinkHighHz,

        @Schema(description = "Downlink frequency, or the lower edge of the downlink band, Hz", example = "145825000")
        Long downlinkLowHz,

        @Schema(description = "Upper edge of the downlink band, Hz")
        Long downlinkHighHz,

        @Schema(description = "Downlink modulation", example = "AFSK")
        String mode,

        @Schema(description = "Uplink modulation", example = "AFSK")
        String uplinkMode,

        @Schema(description = "Symbol rate, baud", example = "1200.0")
        Double baud,

        @Schema(description = "Whether a transponder inverts the spectrum (uplink low maps to downlink high)",
                example = "false")
        boolean invert,

        @Schema(description = "When the entry last changed in the SatNOGS DB, UTC",
                example = "2019-06-16T08:49:33.586159Z")
        Instant updatedAt) {

    static TransmitterResponse from(Transmitter transmitter) {
        return new TransmitterResponse(transmitter.uuid(), transmitter.description(), transmitter.type(),
                transmitter.status(), transmitter.uplinkLowHz(), transmitter.uplinkHighHz(),
                transmitter.downlinkLowHz(), transmitter.downlinkHighHz(), transmitter.mode(),
                transmitter.uplinkMode(), transmitter.baud(), transmitter.invert(), transmitter.updatedAt());
    }
}
