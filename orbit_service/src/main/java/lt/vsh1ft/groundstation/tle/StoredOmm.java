package lt.vsh1ft.groundstation.tle;

import java.time.Instant;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.With;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Table("omm")
@JsonNaming(PropertyNamingStrategies.UpperSnakeCaseStrategy.class)
public record StoredOmm(
        @Id Long id,
        @JsonProperty("NORAD_CAT_ID") int noradId,
        String objectName,
        String objectId,
        LocalDateTime epoch,
        double meanMotion,
        double eccentricity,
        double inclination,
        double raOfAscNode,
        double argOfPericenter,
        double meanAnomaly,
        int ephemerisType,
        String classificationType,
        int elementSetNo,
        int revAtEpoch,
        double bstar,
        double meanMotionDot,
        double meanMotionDdot,
        @With Instant downloadedAt) {
}
