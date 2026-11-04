package lt.vsh1ft.groundstation.elements;

import java.time.LocalDateTime;

public final class OmmFixtures {

    public static final StoredOmm ISS = new StoredOmm(
            null, 25544, "ISS (ZARYA)", "1998-067A", LocalDateTime.parse("2026-11-01T18:56:25.712736"),
            15.49143202, 0.0007095, 51.6332, 339.1679, 336.8847, 23.1822,
            0, "U", 999, 59308, 0.00011503, 5.925e-5, 0, null);

    private OmmFixtures() {
    }
}
