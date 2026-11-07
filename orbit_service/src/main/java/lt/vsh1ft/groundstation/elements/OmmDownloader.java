package lt.vsh1ft.groundstation.elements;

import java.time.Clock;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.orekit.errors.OrekitException;
import org.springframework.stereotype.Component;

@Slf4j
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

    public List<StoredOmm> downloadGroup(String group) {
        final var downloadedAt = clock.instant();
        return client.group(group).stream()
                .map(omm -> omm.withDownloadedAt(downloadedAt))
                .filter(OmmDownloader::isUsable)
                .toList();
    }

    private static boolean isUsable(StoredOmm omm) {
        try {
            OmmTleConverter.toTle(omm);
            return true;
        } catch (OrekitException | IllegalArgumentException e) {
            log.warn("Skipping unusable OMM for {}: {}", omm.noradId(), e.getMessage());
            return false;
        }
    }
}
