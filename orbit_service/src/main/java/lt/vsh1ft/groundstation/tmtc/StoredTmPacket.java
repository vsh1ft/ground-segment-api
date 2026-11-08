package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;

import lt.vsh1ft.ccsds.MissionTime;
import lt.vsh1ft.ccsds.TmPacket;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("tm_packet")
record StoredTmPacket(
        @Id Long id,
        int noradId,
        int apid,
        int sequenceCount,
        int serviceType,
        int messageSubtype,
        Instant generationTime,
        Instant receptionTime,
        byte[] raw) {

    static StoredTmPacket received(NoradId satellite, TmPacket packet, Instant receivedAt) {
        final var header = packet.secondaryHeader();
        return new StoredTmPacket(null, satellite.value(), packet.apid().value(), packet.sequenceCount(),
                header.messageType().serviceType(), header.messageType().messageSubtype(),
                MissionTime.toUtc(header.time()), receivedAt, packet.encode());
    }
}
