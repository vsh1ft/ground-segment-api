package lt.vsh1ft.groundstation.elements;

import java.time.Clock;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.orekit.errors.OrekitException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OmmDownloader {

    private final CelestrakClient client;
    private final Clock clock;

    public StoredOmm download(NoradId satellite) {
        final var downloaded = client.latest(satellite).withDownloadedAt(clock.instant());
        try {
            OmmTleConverter.toTle(downloaded);
        } catch (OrekitException | IllegalArgumentException e) {
            throw new CelestrakException("Unusable OMM for " + satellite + ": " + e.getMessage(), e);
        }
        return downloaded;
    }
}
