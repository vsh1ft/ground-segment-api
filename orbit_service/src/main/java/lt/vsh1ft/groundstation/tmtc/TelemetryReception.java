package lt.vsh1ft.groundstation.tmtc;

import java.time.Instant;
import java.util.Arrays;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.ccsds.MissionTime;
import lt.vsh1ft.ccsds.TmPacket;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.xtce.MissionDatabase;
import lt.vsh1ft.xtce.PacketDecodingException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class TelemetryReception {

    private final TmPacketArchive packets;
    private final ParameterArchive parameters;
    private final MissionDatabases databases;

    @Transactional
    public void receive(NoradId satellite, TmPacket packet, Instant receivedAt) {
        final long packetId = packets.store(satellite, packet, receivedAt);
        databases.of(satellite).ifPresent(database -> decode(database, satellite, packetId, packet));
    }

    private void decode(MissionDatabase database, NoradId satellite, long packetId, TmPacket packet) {
        final var raw = packet.encode();
        try {
            final var decoded = database.decode(Arrays.copyOf(raw, raw.length - TmPacket.CRC_LENGTH));
            if (!decoded.complete()) {
                log.warn("Packet {} of satellite {} matches no complete container of {}; it ends in {}", packetId,
                        satellite, database.name(), decoded.container().name());
                return;
            }
            parameters.store(satellite, packetId, MissionTime.toUtc(packet.secondaryHeader().time()),
                    decoded.values());
        } catch (PacketDecodingException e) {
            log.warn("Packet {} of satellite {} not decoded with {}: {}", packetId, satellite, database.name(),
                    e.getMessage());
        }
    }
}
