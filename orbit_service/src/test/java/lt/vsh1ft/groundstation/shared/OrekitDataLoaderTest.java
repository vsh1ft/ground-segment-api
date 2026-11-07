package lt.vsh1ft.groundstation.shared;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.File;

import org.junit.jupiter.api.Test;

class OrekitDataLoaderTest {

    @Test
    void missingOrekitDataFolderStopsStartupWithDownloadInstructions() {
        assertThatIllegalStateException().isThrownBy(() -> new OrekitDataLoader(new File("no-such-folder")))
                .withMessageStartingWith("Orekit data folder not found")
                .withMessageContaining("orekit-data-main.zip");
    }
}
