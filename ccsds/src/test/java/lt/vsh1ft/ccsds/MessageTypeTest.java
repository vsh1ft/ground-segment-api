package lt.vsh1ft.ccsds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MessageTypeTest {

    @Test
    void housekeepingParameterReportIsService3Subtype25() {
        assertThat(MessageType.HOUSEKEEPING_PARAMETER_REPORT).isEqualTo(new MessageType(3, 25));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 256})
    void serviceTypeOutsideOneByteIsRejected(int serviceType) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MessageType(serviceType, 25))
                .withMessage("service type must be between 0 and 255, was " + serviceType);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 256})
    void messageSubtypeOutsideOneByteIsRejected(int messageSubtype) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MessageType(3, messageSubtype))
                .withMessage("message subtype must be between 0 and 255, was " + messageSubtype);
    }
}
