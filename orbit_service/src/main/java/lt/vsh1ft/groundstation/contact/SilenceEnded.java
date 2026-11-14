package lt.vsh1ft.groundstation.contact;

import java.time.Instant;

import lt.vsh1ft.groundstation.shared.NoradId;

public record SilenceEnded(NoradId satellite, Instant at) implements SilenceChange {
}
