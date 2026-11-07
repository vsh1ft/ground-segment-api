package lt.vsh1ft.groundstation.radio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SatnogsTransmitterTest {

    private static final Instant UPDATED = Instant.parse("2019-06-16T08:49:33.586159Z");
    private static final Instant SYNCED_AT = Instant.parse("2026-11-07T12:00:00Z");

    @Test
    void satnogsTransmitterBecomesAStoredTransmitterStampedWithTheSyncTime() {
        final var satnogs = new SatnogsTransmitter("ZJxCeQmih9zDfYNVrB4wRN", 25544, "Mode V APRS", "Transceiver",
                "active", 145825000L, null, 145825000L, null, "AFSK", "AFSK", 1200.0, false, UPDATED);

        assertThat(satnogs.toTransmitter(SYNCED_AT)).isEqualTo(new Transmitter("ZJxCeQmih9zDfYNVrB4wRN", 25544,
                "Mode V APRS", TransmitterType.TRANSCEIVER, TransmitterStatus.ACTIVE, 145825000L, null, 145825000L,
                null, "AFSK", "AFSK", 1200.0, false, UPDATED, SYNCED_AT));
    }

    @Test
    void unknownTypeIsRejected() {
        final var satnogs = withTypeAndStatus("Repeater", "active");

        assertThatIllegalArgumentException().isThrownBy(() -> satnogs.toTransmitter(SYNCED_AT));
    }

    @Test
    void missingStatusIsRejected() {
        final var satnogs = withTypeAndStatus("Transmitter", null);

        assertThatNullPointerException().isThrownBy(() -> satnogs.toTransmitter(SYNCED_AT));
    }

    private static SatnogsTransmitter withTypeAndStatus(String type, String status) {
        return new SatnogsTransmitter("uuid", 25544, "Beacon", type, status, null, null, 437800000L, null, "FSK",
                null, 9600.0, false, UPDATED);
    }
}
