package lt.vsh1ft.groundstation.orekit;

import java.io.File;

import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class OrekitDataLoader {

    private static final String DOWNLOAD_URL = "https://gitlab.orekit.org/orekit/orekit-data/-/archive/main/orekit-data-main.zip";

    public OrekitDataLoader(@Value("${groundstation.orekit-data-path}") File orekitData) {
        load(orekitData);
    }

    private void load(File orekitData) {
        if (!orekitData.isDirectory()) {
            throw new IllegalStateException("Orekit data folder not found: " + orekitData.getAbsolutePath()
                    + ". Download " + DOWNLOAD_URL + ", unzip it there and rename it 'orekit-data'.");
        }
        final var manager = DataContext.getDefault().getDataProvidersManager();
        if (manager.getProviders().isEmpty()) {
            manager.addProvider(new DirectoryCrawler(orekitData));
        }
    }
}
