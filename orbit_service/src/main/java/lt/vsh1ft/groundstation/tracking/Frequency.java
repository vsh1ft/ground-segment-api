package lt.vsh1ft.groundstation.tracking;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A nominal frequency whose Doppler-corrected value every track point gives")
public record Frequency(
        @Schema(description = "SatNOGS DB identifier of the transmitter; null for a requested frequency",
                example = "ZJxCeQmih9zDfYNVrB4wRN", nullable = true)
        String transmitter,

        @Schema(description = "What the transmitter is used for, from the SatNOGS DB; `requested` for a frequency "
                + "given in the request", example = "Mode V APRS")
        String description,

        @Schema(description = "Receive (DOWNLINK) or transmit (UPLINK) frequency", example = "DOWNLINK")
        Direction direction,

        @Schema(description = "Nominal frequency, Hz", example = "145825000")
        long nominalHz) {

    static final double SPEED_OF_LIGHT_KM_PER_S = 299_792.458;

    long correctedHz(double rangeRateKmPerS) {
        final var factor = 1 - rangeRateKmPerS / SPEED_OF_LIGHT_KM_PER_S;
        return Math.round(direction == Direction.DOWNLINK ? nominalHz * factor : nominalHz / factor);
    }
}
