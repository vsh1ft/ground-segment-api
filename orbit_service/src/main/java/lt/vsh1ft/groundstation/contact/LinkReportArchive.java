package lt.vsh1ft.groundstation.contact;

import static lt.vsh1ft.groundstation.shared.SqlTime.instant;
import static lt.vsh1ft.groundstation.shared.SqlTime.utc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LinkReportArchive {

    private final JdbcClient jdbc;

    void save(LinkReport report) {
        jdbc.sql("""
                        INSERT INTO link_report (norad_id, pass_start, pass_end, frames_received, frames_lost,
                                                 frames_failed_check, idle_frames, updated_at)
                        VALUES (:noradId, :passStart, :passEnd, :framesReceived, :framesLost, :framesFailedCheck,
                                :idleFrames, :updatedAt)
                        ON CONFLICT (norad_id, pass_start) DO UPDATE SET
                            pass_end = EXCLUDED.pass_end,
                            frames_received = EXCLUDED.frames_received,
                            frames_lost = EXCLUDED.frames_lost,
                            frames_failed_check = EXCLUDED.frames_failed_check,
                            idle_frames = EXCLUDED.idle_frames,
                            updated_at = EXCLUDED.updated_at
                        """)
                .param("noradId", report.noradId())
                .param("passStart", utc(report.passStart()))
                .param("passEnd", utc(report.passEnd()))
                .param("framesReceived", report.framesReceived())
                .param("framesLost", report.framesLost())
                .param("framesFailedCheck", report.framesFailedCheck())
                .param("idleFrames", report.idleFrames())
                .param("updatedAt", utc(report.updatedAt()))
                .update();
    }

    List<LinkReport> newestFirst(NoradId satellite, int limit) {
        return jdbc.sql("""
                        SELECT norad_id, pass_start, pass_end, frames_received, frames_lost, frames_failed_check,
                               idle_frames, updated_at
                        FROM link_report WHERE norad_id = :noradId ORDER BY pass_start DESC LIMIT :limit
                        """)
                .param("noradId", satellite.value())
                .param("limit", limit)
                .query(LinkReportArchive::report)
                .list();
    }

    private static LinkReport report(ResultSet row, int rowNumber) throws SQLException {
        return new LinkReport(row.getInt("norad_id"), instant(row, "pass_start"), instant(row, "pass_end"),
                row.getLong("frames_received"), row.getLong("frames_lost"), row.getLong("frames_failed_check"),
                row.getLong("idle_frames"), instant(row, "updated_at"));
    }
}
