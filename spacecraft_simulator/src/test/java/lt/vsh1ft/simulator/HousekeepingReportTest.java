package lt.vsh1ft.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HousekeepingReportTest {

    @Test
    void reportIsStructureIdThenVoltageTemperatureAndMode() {
        final var report = new HousekeepingReport(7400, -50, SpacecraftMode.NOMINAL);

        assertThat(report.toUserData()).isEqualTo(HexFormat.ofDelimiter(" ").parseHex("01 1C E8 FF CE 01"));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 65_536})
    void batteryVoltageOutsideSixteenBitsUnsignedIsRejected(int millivolts) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HousekeepingReport(millivolts, 0, SpacecraftMode.NOMINAL));
    }

    @ParameterizedTest
    @ValueSource(ints = {-32_769, 32_768})
    void temperatureOutsideSixteenBitsSignedIsRejected(int deciCelsius) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HousekeepingReport(7400, deciCelsius, SpacecraftMode.NOMINAL));
    }
}
