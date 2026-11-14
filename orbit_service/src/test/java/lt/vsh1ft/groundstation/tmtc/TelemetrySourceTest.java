package lt.vsh1ft.groundstation.tmtc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TelemetrySourceTest {

    @Test
    void sourceIsNamedByWhatTheValuesWereDecodedFrom() {
        assertThat(TelemetrySource.packet(12)).hasToString("packet 12");
        assertThat(TelemetrySource.frame(7)).hasToString("frame 7");
    }
}
