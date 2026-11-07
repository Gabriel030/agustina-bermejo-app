package com.red_escuela.backend.billing.simulation;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class SimulationArchitectureTest {

    private static final JavaClasses SIMULATION = new ClassFileImporter().withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .importPackages("com.red_escuela.backend.billing.simulation");

    @Test
    void scenarioChangesStateOnlyThroughUseCases() {
        noClasses().that().haveSimpleName("Scenario")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .orShould().dependOnClassesThat().areAssignableTo(EntityManager.class)
                .check(SIMULATION);
    }
}
