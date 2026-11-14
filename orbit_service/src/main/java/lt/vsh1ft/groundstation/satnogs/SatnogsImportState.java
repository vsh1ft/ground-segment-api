package lt.vsh1ft.groundstation.satnogs;

import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import lt.vsh1ft.groundstation.shared.SqlTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SatnogsImportState {

    private final JdbcClient jdbc;

    Optional<Instant> importedUntil(NoradId satellite) {
        return jdbc.sql("SELECT imported_until FROM satnogs_import WHERE norad_id = :noradId")
                .param("noradId", satellite.value())
                .query((row, number) -> SqlTime.instant(row, "imported_until"))
                .optional();
    }

    void save(NoradId satellite, Instant importedUntil) {
        jdbc.sql("""
                        INSERT INTO satnogs_import (norad_id, imported_until) VALUES (:noradId, :importedUntil)
                        ON CONFLICT (norad_id) DO UPDATE SET imported_until = EXCLUDED.imported_until
                        """)
                .param("noradId", satellite.value())
                .param("importedUntil", SqlTime.utc(importedUntil))
                .update();
    }
}
