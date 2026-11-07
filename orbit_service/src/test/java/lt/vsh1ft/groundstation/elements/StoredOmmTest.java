package lt.vsh1ft.groundstation.elements;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class StoredOmmTest {

    private static final String CELESTRAK_JSON = """
            [{"OBJECT_NAME":"ISS (ZARYA)","OBJECT_ID":"1998-067A","EPOCH":"2026-11-01T18:56:25.712736",\
            "MEAN_MOTION":15.49143202,"ECCENTRICITY":0.0007095,"INCLINATION":51.6332,"RA_OF_ASC_NODE":339.1679,\
            "ARG_OF_PERICENTER":336.8847,"MEAN_ANOMALY":23.1822,"EPHEMERIS_TYPE":0,"CLASSIFICATION_TYPE":"U",\
            "NORAD_CAT_ID":25544,"ELEMENT_SET_NO":999,"REV_AT_EPOCH":59308,"BSTAR":0.00011503,\
            "MEAN_MOTION_DOT":5.925e-5,"MEAN_MOTION_DDOT":0}]
            """;

    @Test
    @Requirement("IF-010")
    void celestrakJsonMapsToTheColumns() {
        final var omm = JsonMapper.builder().build().readValue(CELESTRAK_JSON, StoredOmm[].class)[0];

        assertThat(omm.noradId()).isEqualTo(25544);
        assertThat(omm.objectName()).isEqualTo("ISS (ZARYA)");
        assertThat(omm.objectId()).isEqualTo("1998-067A");
        assertThat(omm.epoch()).isEqualTo(LocalDateTime.parse("2026-11-01T18:56:25.712736"));
        assertThat(omm.meanMotion()).isEqualTo(15.49143202);
        assertThat(omm.raOfAscNode()).isEqualTo(339.1679);
        assertThat(omm.meanMotionDot()).isEqualTo(5.925e-5);
        assertThat(omm.downloadedAt()).isNull();
    }
}
