package com.red_escuela.backend.architecture.modulith;

import com.red_escuela.backend.BackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleDocumentationTest {

    private static final String UPDATE_PROPERTY = "modulith.docs.update";
    private static final String COMMITTED_FOLDER = "../docs/architecture";
    private static final String SCRATCH_FOLDER = "target/modulith-docs";
    private static final String RELATION_PREFIX = "Rel(";

    @Test
    void writesModuleDiagramsAndCanvases() throws IOException {
        String outputFolder = Boolean.getBoolean(UPDATE_PROPERTY) ? COMMITTED_FOLDER : SCRATCH_FOLDER;
        ApplicationModules modules = ApplicationModules.of(BackendApplication.class);

        new Documenter(modules, outputFolder)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
        sortRelationsOfEveryDiagram(Path.of(outputFolder));

        assertThat(Path.of(outputFolder, "components.puml")).exists();
        try (Stream<Path> files = Files.list(Path.of(outputFolder))) {
            assertThat(files.count()).isGreaterThan(1);
        }
    }

    private void sortRelationsOfEveryDiagram(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            for (Path diagram : files.filter(path -> path.toString().endsWith(".puml")).toList()) {
                Files.write(diagram, withSortedRelations(Files.readAllLines(diagram)));
            }
        }
    }

    private List<String> withSortedRelations(List<String> lines) {
        List<String> relations = lines.stream().filter(line -> line.startsWith(RELATION_PREFIX)).sorted().toList();
        List<String> result = new ArrayList<>();
        int next = 0;
        for (String line : lines) {
            result.add(line.startsWith(RELATION_PREFIX) ? relations.get(next++) : line);
        }
        return result;
    }
}
