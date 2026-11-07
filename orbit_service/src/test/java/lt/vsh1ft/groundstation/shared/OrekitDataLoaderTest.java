package lt.vsh1ft.groundstation.shared;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.File;

import lt.vsh1ft.groundstation.traceability.Requirement;
import org.junit.jupiter.api.Test;

class OrekitDataLoaderTest {

    @Test
    @Requirement("OPS-010")
    void missingOrekitDataFolderStopsStartupWithDownloadInstructions() {
        assertThatIllegalStateException().isThrownBy(() -> new OrekitDataLoader(new File("no-such-folder")))
                .withMessageStartingWith("Orekit data folder not found")
                .withMessageContaining("orekit-data-main.zip");
    }
}
