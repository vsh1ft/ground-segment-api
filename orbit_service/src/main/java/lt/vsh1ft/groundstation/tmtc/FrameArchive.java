package lt.vsh1ft.groundstation.tmtc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FrameArchive {

    private final JdbcClient jdbc;

    Optional<Long> find(NoradId satellite, byte[] raw) {
        return jdbc.sql("SELECT id FROM received_frame WHERE norad_id = :noradId AND raw_sha256 = sha256(:raw)")
                .param("noradId", satellite.value())
                .param("raw", raw)
                .query(Long.class)
                .optional();
    }

    Optional<Long> insert(NoradId satellite, byte[] raw, Instant receivedAt, Decoding decoding) {
        return jdbc.sql("""
                        INSERT INTO received_frame (norad_id, raw, first_received_at, generation_time, container,
                                                    crc_checked)
                        VALUES (:noradId, :raw, :receivedAt, :generationTime, :container, :crcChecked)
                        ON CONFLICT (norad_id, raw_sha256) DO NOTHING
                        RETURNING id
                        """)
                .param("noradId", satellite.value())
                .param("raw", raw)
                .param("receivedAt", utc(receivedAt))
                .param("generationTime", decoding.generationTime() == null ? null : utc(decoding.generationTime()))
                .param("container", decoding.container())
                .param("crcChecked", decoding.crcChecked())
                .query(Long.class)
                .optional();
    }

    boolean addReception(long frameId, Reception reception) {
        return jdbc.sql("""
                        INSERT INTO frame_reception (frame_id, source, station_id, station, latitude_deg,
                                                     longitude_deg, altitude_m, observation, received_at)
                        VALUES (:frameId, :source, :stationId, :station, :latitudeDeg, :longitudeDeg, :altitudeM,
                                :observation, :receivedAt)
                        ON CONFLICT DO NOTHING
                        """)
                .param("frameId", frameId)
                .param("source", reception.source().name())
                .param("stationId", reception.stationId())
                .param("station", reception.station())
                .param("latitudeDeg", reception.latitudeDeg())
                .param("longitudeDeg", reception.longitudeDeg())
                .param("altitudeM", reception.altitudeM())
                .param("observation", reception.observation())
                .param("receivedAt", utc(reception.receivedAt()))
                .update() == 1;
    }

    FrameResponse frame(long id) {
        return withReceptions(jdbc.sql(FRAME_COLUMNS + " WHERE id = :id").param("id", id)
                .query(FrameArchive::frame).list()).getFirst();
    }

    FramesResponse frames(NoradId satellite, FramesQuery query) {
        final var conditions = new ArrayList<String>();
        conditions.add("norad_id = :noradId");
        if (query.before() != null) {
            conditions.add("id < :before");
        }
        if (query.source() != null) {
            conditions.add("EXISTS (SELECT 1 FROM frame_reception r WHERE r.frame_id = received_frame.id "
                    + "AND r.source = :source)");
        }
        if (query.station() != null) {
            conditions.add("EXISTS (SELECT 1 FROM frame_reception r WHERE r.frame_id = received_frame.id "
                    + "AND r.station_id = :station)");
        }
        final var found = jdbc.sql(FRAME_COLUMNS + " WHERE " + String.join(" AND ", conditions)
                        + " ORDER BY id DESC LIMIT :limit")
                .param("noradId", satellite.value())
                .param("before", query.before())
                .param("source", query.source() == null ? null : query.source().name())
                .param("station", query.station())
                .param("limit", query.limit() + 1)
                .query(FrameArchive::frame)
                .list();
        final var page = withReceptions(found.stream().limit(query.limit()).toList());
        return new FramesResponse(page, found.size() > query.limit() ? page.getLast().id() : null);
    }

    private List<FrameResponse> withReceptions(List<FrameResponse> frames) {
        if (frames.isEmpty()) {
            return frames;
        }
        final Map<Long, List<FrameResponse.Reception>> receptions = jdbc.sql("""
                        SELECT frame_id, source, station, station_id, latitude_deg, longitude_deg, altitude_m,
                               observation, received_at
                        FROM frame_reception WHERE frame_id IN (:ids) ORDER BY received_at, id
                        """)
                .param("ids", frames.stream().map(FrameResponse::id).toList())
                .query((row, number) -> Map.entry(row.getLong("frame_id"), reception(row)))
                .list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        return frames.stream().map(frame -> new FrameResponse(frame.id(), frame.firstReceivedAt(), frame.container(),
                frame.generationTime(), frame.crcChecked(), frame.length(), frame.raw(),
                receptions.getOrDefault(frame.id(), List.of()))).toList();
    }

    private static final String FRAME_COLUMNS = """
            SELECT id, raw, first_received_at, generation_time, container, crc_checked FROM received_frame""";

    private static FrameResponse frame(ResultSet row, int number) throws SQLException {
        final var raw = row.getBytes("raw");
        return new FrameResponse(row.getLong("id"), instant(row, "first_received_at"), row.getString("container"),
                instant(row, "generation_time"), row.getObject("crc_checked", Boolean.class), raw.length,
                HexFormat.of().withUpperCase().formatHex(raw), List.of());
    }

    private static FrameResponse.Reception reception(ResultSet row) throws SQLException {
        return new FrameResponse.Reception(ReceptionSource.valueOf(row.getString("source")), row.getString("station"),
                row.getObject("station_id", UUID.class), row.getObject("latitude_deg", Double.class),
                row.getObject("longitude_deg", Double.class), row.getObject("altitude_m", Double.class),
                row.getObject("observation", Long.class), instant(row, "received_at"));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        final var time = row.getObject(column, OffsetDateTime.class);
        return time == null ? null : time.toInstant();
    }

    record Decoding(String container, Instant generationTime, Boolean crcChecked) {
    }
}
