package lt.vsh1ft.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;

import org.junit.jupiter.api.Test;

class EventReportTest {

    @Test
    void eventWithAValueCarriesItsIdAndTheValueAsSixteenBits() {
        assertThat(hex(EventReport.of(EventDefinition.BATTERY_LOW, 7149))).isEqualTo("00011bed");
        assertThat(hex(EventReport.of(EventDefinition.TEMPERATURE_NORMAL, -50))).isEqualTo("0004ffce");
    }

    @Test
    void eventWithoutAValueCarriesOnlyItsId() {
        assertThat(hex(EventReport.of(EventDefinition.SAFE_MODE_ENTERED))).isEqualTo("0005");
    }

    private static String hex(EventReport report) {
        return HexFormat.of().formatHex(report.toUserData());
    }
}
