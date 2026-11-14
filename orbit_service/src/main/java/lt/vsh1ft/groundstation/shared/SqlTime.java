package lt.vsh1ft.groundstation.shared;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class SqlTime {

    private SqlTime() {
    }

    public static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    public static Instant instant(ResultSet row, String column) throws SQLException {
        final var time = row.getObject(column, OffsetDateTime.class);
        return time == null ? null : time.toInstant();
    }
}
