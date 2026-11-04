package lt.vsh1ft.groundstation.catalog;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("satellite_summary")
@Schema(description = "A satellite in the catalog")
public record SatelliteSummary(
        @Schema(description = "NORAD catalog number", example = "25544")
        @Id int noradId,

        @Schema(description = "Name as published by CelesTrak", example = "ISS (ZARYA)")
        String name,

        @Schema(description = "COSPAR international designator: launch year, launch number and piece",
                example = "1998-067A")
        String internationalDesignator,

        @Schema(description = "When the satellite was added to the catalog, UTC", example = "2026-11-04T10:15:30Z")
        Instant addedAt,

        @Schema(description = "Epoch of the newest stored orbital elements, UTC", example = "2026-11-04T06:12:45.123Z")
        Instant latestEpoch) {
}
