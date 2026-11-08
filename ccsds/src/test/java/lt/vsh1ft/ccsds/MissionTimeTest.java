package lt.vsh1ft.ccsds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class MissionTimeTest {

    @Test
    void midnightUtcIsCountedInTaiSecondsSince1958() {
        assertThat(MissionTime.toCuc(Instant.parse("2026-11-08T00:00:00Z")))
                .isEqualTo(new CucTime(2_172_787_237L, 0));
    }

    @Test
    void halfASecondIsAFineTimeOf8000() {
        assertThat(MissionTime.toCuc(Instant.parse("2026-11-08T00:00:00.5Z")).fine()).isEqualTo(0x8000);
    }

    @Test
    void cucTimeIsConvertedBackToUtc() {
        assertThat(MissionTime.toUtc(new CucTime(2_172_787_237L, 0x8000)))
                .isEqualTo(Instant.parse("2026-11-08T00:00:00.5Z"));
    }

    @Test
    void roundTripLosesLessThanOneFineTimeUnit() {
        final var utc = Instant.parse("2026-11-08T12:34:56.789123456Z");

        final var back = MissionTime.toUtc(MissionTime.toCuc(utc));

        assertThat(Duration.between(back, utc)).isBetween(Duration.ZERO, Duration.ofNanos(15_259));
    }

    @Test
    void lastNanosecondOfASecondIsTheLargestFineTimeAndNeverRollsOver() {
        final var cuc = MissionTime.toCuc(Instant.parse("2026-11-08T12:34:56.999999999Z"));

        assertThat(cuc.fine()).isEqualTo(0xFFFF);
        assertThat(cuc.coarse()).isEqualTo(MissionTime.toCuc(Instant.parse("2026-11-08T12:34:56Z")).coarse());
    }

    @Test
    void timeBeforeTheEpochCannotBeEncoded() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MissionTime.toCuc(Instant.parse("1957-12-31T23:59:00Z")));
    }
}
