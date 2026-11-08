package lt.vsh1ft.ccsds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

class IdlePacketTest {

    @Test
    void shortestIdlePacketIsAHeaderAndOneFillByte() {
        assertThat(IdlePacket.of(7)).isEqualTo(Hex.bytes("07 FF C0 00 00 00 55"));
    }

    @Test
    void idlePacketHasApid2047AndNoSecondaryHeader() {
        final var header = PrimaryHeader.readFrom(ByteBuffer.wrap(IdlePacket.of(30)));

        assertThat(header.apid()).isEqualTo(IdlePacket.APID);
        assertThat(header.secondaryHeader()).isFalse();
        assertThat(header.dataFieldLength()).isEqualTo(24);
    }

    @Test
    void idlePacketCannotBeShorterThanSevenOrLongerThanAnyPacket() {
        assertThatIllegalArgumentException().isThrownBy(() -> IdlePacket.of(6));
        assertThatIllegalArgumentException().isThrownBy(() -> IdlePacket.of(65_543));
        assertThat(IdlePacket.of(65_542)).hasSize(65_542);
    }
}
