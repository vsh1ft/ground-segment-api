package lt.vsh1ft.groundstation.tmtc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import lt.vsh1ft.ccsds.Apid;
import lt.vsh1ft.ccsds.CucTime;
import lt.vsh1ft.ccsds.MessageType;
import lt.vsh1ft.ccsds.PusTmSecondaryHeader;
import lt.vsh1ft.ccsds.TmPacket;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;

class StoredTmPacketTest {

    private static final NoradId SATELLITE = new NoradId(99999);
    private static final Instant RECEIVED = Instant.parse("2026-11-08T10:00:00Z");

    @Test
    @Requirement("IF-050")
    void generationTimeIsTheOnboardTaiTimeConvertedToUtc() {
        final var header = new PusTmSecondaryHeader(0, MessageType.HOUSEKEEPING_PARAMETER_REPORT, 1, 0,
                new CucTime(2_172_787_237L, 0x8000));
        final var packet = new TmPacket(new Apid(100), 5, header, new byte[0]);

        assertThat(StoredTmPacket.received(SATELLITE, packet, RECEIVED).generationTime())
                .isEqualTo(Instant.parse("2026-11-08T00:00:00.5Z"));
    }

    @Test
    @Requirement({"TM-010", "IF-040"})
    void storedPacketKeepsItsSatelliteTheHeaderFieldsAndTheBytesAsReceived() {
        final var stored = StoredTmPacket.received(SATELLITE, TmPacket.decode(TmLinkTest.REFERENCE), RECEIVED);

        assertThat(stored.noradId()).isEqualTo(99999);
        assertThat(stored.apid()).isEqualTo(100);
        assertThat(stored.sequenceCount()).isEqualTo(5);
        assertThat(stored.serviceType()).isEqualTo(3);
        assertThat(stored.messageSubtype()).isEqualTo(25);
        assertThat(stored.receptionTime()).isEqualTo(RECEIVED);
        assertThat(stored.raw()).isEqualTo(TmLinkTest.REFERENCE);
    }
}
