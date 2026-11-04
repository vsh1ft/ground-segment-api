package lt.vsh1ft.groundstation.satellite;

import java.time.Instant;

import lt.vsh1ft.groundstation.tle.StoredOmm;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("satellite")
record Satellite(@Id Integer noradId, String name, String internationalDesignator, Instant addedAt) {

    static Satellite from(StoredOmm omm) {
        return new Satellite(omm.noradId(), omm.objectName(), omm.objectId(), omm.downloadedAt());
    }
}
