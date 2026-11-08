package lt.vsh1ft.groundstation.elements;

import java.time.Clock;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lt.vsh1ft.groundstation.shared.NoradId;
import org.orekit.errors.OrekitException;
import org.orekit.propagation.analytical.tle.TLE;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TleReader {

    private static final int MAX_NAME_LENGTH = 100;

    private final Clock clock;

    public StoredOmm read(NoradId satellite, String text) {
        final List<String> lines = text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        if (lines.size() != 3) {
            throw new InvalidTleException("A TLE has three lines, a name and two element lines, but "
                    + lines.size() + " were sent");
        }
        final var name = lines.getFirst();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new InvalidTleException("The name line is longer than " + MAX_NAME_LENGTH + " characters");
        }
        final var tle = parse(lines.get(1), lines.get(2));
        if (tle.getSatelliteNumber() != satellite.value()) {
            throw new InvalidTleException("The TLE is for NORAD ID " + tle.getSatelliteNumber() + ", not "
                    + satellite);
        }
        final var omm = OmmTleConverter.toOmm(name, tle, clock.instant());
        try {
            InternationalDesignator.parse(omm.objectId());
        } catch (IllegalArgumentException e) {
            throw new InvalidTleException("The TLE has no usable international designator: " + e.getMessage(), e);
        }
        return omm;
    }

    private static TLE parse(String line1, String line2) {
        try {
            if (!TLE.isFormatOK(line1, line2)) {
                throw new InvalidTleException("The element lines are not in the TLE format: each has 69 "
                        + "characters, starts with its line number and has its fields in fixed columns");
            }
            return new TLE(line1, line2);
        } catch (OrekitException e) {
            throw new InvalidTleException("The element lines are not a valid TLE: " + e.getMessage(), e);
        }
    }
}
