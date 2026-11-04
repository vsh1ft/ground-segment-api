package lt.vsh1ft.groundstation.elements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class InternationalDesignatorTest {

    @Test
    void issDesignatorIsSplitIntoYearNumberAndPiece() {
        assertThat(InternationalDesignator.parse("1998-067A"))
                .isEqualTo(new InternationalDesignator(1998, 67, "A"));
    }

    @Test
    void multiLetterPieceIsKept() {
        assertThat(InternationalDesignator.parse("2019-029BJ").piece()).isEqualTo("BJ");
    }

    @Test
    void textThatIsNotADesignatorIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> InternationalDesignator.parse("UNKNOWN"));
    }
}
