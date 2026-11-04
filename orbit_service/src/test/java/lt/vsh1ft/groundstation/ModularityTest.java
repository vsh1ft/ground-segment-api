package lt.vsh1ft.groundstation;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(GroundStationApplication.class);

    @Test
    void modulesHaveNoCyclesAndUseOnlyEachOthersPublicApi() {
        MODULES.verify();
    }

    @Test
    void moduleDocumentationIsWritten() {
        new Documenter(MODULES)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
