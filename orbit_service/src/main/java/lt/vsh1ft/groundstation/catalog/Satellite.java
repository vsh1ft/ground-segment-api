package lt.vsh1ft.groundstation.catalog;

import java.time.Instant;

import lt.vsh1ft.groundstation.elements.StoredOmm;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("satellite")
record Satellite(@Id Integer noradId, String name, String internationalDesignator, Instant addedAt) {

    static Satellite from(StoredOmm omm) {
        return new Satellite(omm.noradId(), omm.objectName(), omm.objectId(), omm.downloadedAt());
    }

    Satellite renamedFrom(StoredOmm omm) {
        return new Satellite(noradId, omm.objectName(), omm.objectId(), addedAt);
    }
}
