package com.red_escuela.backend.architecture.modulith;

import com.red_escuela.backend.testsupport.ImportedApplicationClasses;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.DependencyType;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Analyzes the dependencies of every class of every module of the application, so its time grows with the code base")
class ModularityTest {

    private static final Path BASELINE_DIRECTORY = Path.of("src/test/resources/modulith-baseline");
    private static final String UPDATE_PROPERTY = "modulith.baseline.update";

    private static final List<String> EXPECTED_MODULES = List.of(
            "academicrecord", "academicstructure", "announcement", "attendance", "billing", "config",
            "enrollment", "evaluation", "graduation", "identity", "notification", "shared");

    private final ApplicationModules modules = ImportedApplicationClasses.modules();

    @Test
    void detectsTheDeclaredModules() {
        List<String> detected = modules.stream().map(module -> module.getIdentifier().toString()).sorted().toList();

        assertThat(detected).isEqualTo(EXPECTED_MODULES);
    }

    @Test
    void introducesNoViolationsBeyondTheBaseline() {
        ViolationSnapshot current = ViolationSnapshot.of(modules.detectViolations().getMessages(), moduleDependencies());
        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            ViolationBaselineFiles.write(BASELINE_DIRECTORY, current);
            return;
        }

        ViolationSnapshot.Comparison comparison = current.compareTo(ViolationBaselineFiles.read(BASELINE_DIRECTORY));

        if (!comparison.stale().isEmpty()) {
            System.err.println("Modulith baseline has entries that no longer apply; delete or lower them in "
                    + BASELINE_DIRECTORY + " (regenerate with -D" + UPDATE_PROPERTY + "=true):\n  "
                    + String.join("\n  ", comparison.stale()));
        }
        assertThat(comparison.added())
                .as("New module violations. A module may only use the public API (api, events, model named "
                        + "interfaces) of another module; do not add entries to the baseline")
                .isEmpty();
    }

    private Map<String, Set<String>> moduleDependencies() {
        Map<String, Set<String>> dependencies = new TreeMap<>();
        modules.forEach(module -> dependencies.put(module.getIdentifier().toString(),
                module.getDirectDependencies(modules, DependencyType.values()).stream()
                        .map(dependency -> dependency.getTargetModule().getIdentifier().toString())
                        .collect(Collectors.toCollection(TreeSet::new))));
        return dependencies;
    }
}
