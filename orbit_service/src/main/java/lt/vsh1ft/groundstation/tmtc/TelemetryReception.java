package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;
import java.util.Arrays;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.ccsds.MissionTime;
import lt.vsh1ft.ccsds.TmPacket;
import lt.vsh1ft.groundstation.contact.ContactWatch;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.xtce.DecodedPacket;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class TelemetryReception {

    private final TmPacketArchive packets;
    private final ParameterValueReception values;
    private final EventArchive events;
    private final MissionDatabases databases;
    private final ContactWatch contacts;

    @Transactional
    public void receive(NoradId satellite, TmPacket packet, Instant receivedAt) {
        final var visibility = contacts.received(satellite, receivedAt);
        final long packetId = packets.store(satellite, packet, receivedAt, visibility);
        final var source = TelemetrySource.packet(packetId);
        final var generated = MissionTime.toUtc(packet.secondaryHeader().time());
        final var decoded = databases.decode(satellite, withoutCrc(packet), "Packet " + packetId);
        decoded.ifPresent(found -> receiveValues(satellite, source, generated, found));
        Severity.of(packet.secondaryHeader().messageType()).ifPresent(severity ->
                events.store(StoredEvent.of(satellite, packetId, generated, severity, decoded)));
    }

    private void receiveValues(NoradId satellite, TelemetrySource source, Instant generated, DecodedPacket decoded) {
        if (decoded.complete()) {
            values.receive(satellite, source, generated, decoded.values());
            return;
        }
        log.warn("The {} of satellite {} matches no complete container; it ends in {}", source, satellite,
                decoded.container().name());
    }

    private static byte[] withoutCrc(TmPacket packet) {
        final var raw = packet.encode();
        return Arrays.copyOf(raw, raw.length - TmPacket.CRC_LENGTH);
    }
}
