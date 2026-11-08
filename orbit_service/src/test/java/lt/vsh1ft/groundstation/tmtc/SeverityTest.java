package lt.vsh1ft.groundstation.tmtc;

import static org.assertj.core.api.Assertions.assertThat;

import lt.vsh1ft.ccsds.MessageType;
import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;

class SeverityTest {

    @Test
    @Requirement("EVT-010")
    void severityIsTheSubtypeOfAnEventReport() {
        assertThat(Severity.of(MessageType.INFORMATIVE_EVENT_REPORT)).hasValue(Severity.INFO);
        assertThat(Severity.of(MessageType.LOW_SEVERITY_ANOMALY_REPORT)).hasValue(Severity.LOW);
        assertThat(Severity.of(MessageType.MEDIUM_SEVERITY_ANOMALY_REPORT)).hasValue(Severity.MEDIUM);
        assertThat(Severity.of(MessageType.HIGH_SEVERITY_ANOMALY_REPORT)).hasValue(Severity.HIGH);
    }

    @Test
    void otherMessagesAreNoEvents() {
        assertThat(Severity.of(MessageType.HOUSEKEEPING_PARAMETER_REPORT)).isEmpty();
        assertThat(Severity.of(new MessageType(5, 0))).isEmpty();
        assertThat(Severity.of(new MessageType(5, 8))).isEmpty();
    }

    @Test
    void minimumSeverityIncludesEveryWorseOne() {
        assertThat(Severity.MEDIUM.andWorse()).containsExactly(Severity.MEDIUM, Severity.HIGH);
        assertThat(Severity.INFO.andWorse()).containsExactly(Severity.values());
    }
}
