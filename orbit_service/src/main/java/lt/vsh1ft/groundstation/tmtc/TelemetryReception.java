package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.ccsds.MissionTime;
import lt.vsh1ft.ccsds.TmPacket;
import lt.vsh1ft.groundstation.contact.ContactWatch;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.xtce.DecodedPacket;
import lt.vsh1ft.xtce.MissionDatabase;
import lt.vsh1ft.xtce.PacketDecodingException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class TelemetryReception {

    private final TmPacketArchive packets;
    private final ParameterArchive parameters;
    private final EventArchive events;
    private final MissionDatabases databases;
    private final ApplicationEventPublisher publisher;
    private final ContactWatch contacts;

    @Transactional
    public void receive(NoradId satellite, TmPacket packet, Instant receivedAt) {
        final var visibility = contacts.received(satellite, receivedAt);
        final long packetId = packets.store(satellite, packet, receivedAt, visibility);
        final var generated = MissionTime.toUtc(packet.secondaryHeader().time());
        final var decoded = databases.of(satellite).flatMap(database -> decode(database, satellite, packetId, packet));
        decoded.filter(DecodedPacket::complete).ifPresent(complete -> {
            parameters.store(satellite, packetId, generated, complete.values());
            publisher.publishEvent(new ParameterValuesReceived(satellite, packetId, generated, complete.values()));
        });
        Severity.of(packet.secondaryHeader().messageType()).ifPresent(severity ->
                events.store(StoredEvent.of(satellite, packetId, generated, severity, decoded)));
    }

    private static Optional<DecodedPacket> decode(MissionDatabase database, NoradId satellite, long packetId,
                                                  TmPacket packet) {
        final var raw = packet.encode();
        try {
            final var decoded = database.decode(Arrays.copyOf(raw, raw.length - TmPacket.CRC_LENGTH));
            if (!decoded.complete()) {
                log.warn("Packet {} of satellite {} matches no complete container of {}; it ends in {}", packetId,
                        satellite, database.name(), decoded.container().name());
            }
            return Optional.of(decoded);
        } catch (PacketDecodingException e) {
            log.warn("Packet {} of satellite {} not decoded with {}: {}", packetId, satellite, database.name(),
                    e.getMessage());
            return Optional.empty();
        }
    }
}
