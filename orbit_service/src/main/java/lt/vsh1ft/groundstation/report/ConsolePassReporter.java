package lt.vsh1ft.groundstation.report;

import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import lt.vsh1ft.groundstation.pass.GroundStation;
import lt.vsh1ft.groundstation.pass.Pass;
import lt.vsh1ft.groundstation.pass.PassReporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConsolePassReporter implements PassReporter {

    private static final DateTimeFormatter UTC_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private final PrintStream out = System.out;
    private final DateTimeFormatter localTime;

    public ConsolePassReporter(@Value("${groundstation.time-zone}") ZoneId localZone) {
        this.localTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(localZone);
    }

    @Override
    public void report(GroundStation station, List<Pass> passes) {
        out.printf(Locale.US, "%d passes over %s%n", passes.size(), station.name());
        for (Pass pass : passes) {
            out.println("Pass");
            out.println("  begins  " + format(pass.start()));
            out.printf(Locale.US, "  highest %s  elevation %.1f deg%n",
                    format(pass.peak().time()), pass.peak().elevationDeg());
            out.println("  ends    " + format(pass.end()));
        }
    }

    private String format(Instant instant) {
        return UTC_TIME.format(instant) + " (" + localTime.format(instant) + ")";
    }
}
