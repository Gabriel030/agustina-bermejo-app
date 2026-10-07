package com.red_escuela.backend.architecture.modulith;

import com.red_escuela.backend.BackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleDocumentationTest {

    private static final String OUTPUT_FOLDER = "../docs/architecture";

    @Test
    void writesModuleDiagramsAndCanvases() throws Exception {
        ApplicationModules modules = ApplicationModules.of(BackendApplication.class);

        new Documenter(modules, OUTPUT_FOLDER)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();

        assertThat(Path.of(OUTPUT_FOLDER, "components.puml")).exists();
        assertThat(Files.list(Path.of(OUTPUT_FOLDER)).count()).isGreaterThan(1);
    }
}
