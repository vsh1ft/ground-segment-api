package lt.vsh1ft.groundstation.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FrequencyTest {

    @ParameterizedTest
    @CsvSource({
            "145825000, -7.0, 145828405",
            "145825000, 0.0, 145825000",
            "145825000, 7.0, 145821595",
            "437000000, -7.0, 437010204",
            "436500000, 4.2, 436493885"})
    @Requirement("TRK-020")
    void downlinkIsHigherWhileTheSatelliteApproachesAndLowerWhileItLeaves(long nominalHz, double rangeRate,
                                                                          long correctedHz) {
        assertThat(new Frequency(null, "requested", Direction.DOWNLINK, nominalHz).correctedHz(rangeRate))
                .isEqualTo(correctedHz);
    }

    @ParameterizedTest
    @CsvSource({"145825000, -7.0, 145821595", "145825000, 7.0, 145828405", "145900000, 0.0, 145900000"})
    @Requirement("TRK-020")
    void uplinkIsShiftedTheOtherWaySoTheSatelliteHearsTheNominalFrequency(long nominalHz, double rangeRate,
                                                                         long correctedHz) {
        final var uplink = new Frequency(null, "requested", Direction.UPLINK, nominalHz);

        assertThat(uplink.correctedHz(rangeRate)).isEqualTo(correctedHz);
        final var heardOnBoard = new Frequency(null, "requested", Direction.DOWNLINK, uplink.correctedHz(rangeRate))
                .correctedHz(rangeRate);
        assertThat(heardOnBoard).isEqualTo(nominalHz);
    }

    @Test
    void speedOfLightIsTheExactSiValue() {
        assertThat(Frequency.SPEED_OF_LIGHT_KM_PER_S).isEqualTo(299_792.458);
    }
}
