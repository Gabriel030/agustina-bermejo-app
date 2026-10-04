package com.red_escuela.backend.architecture;

import com.red_escuela.backend.shared.time.RawNowRule;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchitectureTest {

    private static final JavaClasses MAIN_CLASSES = RawNowRule.importMainClasses();

    @Test
    void entitiesAndDtosHaveNoPrimitiveMoneyPercentOrGradeFields() {
        FreezingArchRule.freeze(ArchitectureRules.noPrimitiveMoneyPercentOrGradeFields("..entity..", "..dto..")).check(MAIN_CLASSES);
    }

    @Test
    void primitiveMoneyRuleDetectsViolations() {
        JavaClasses fixture = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages(ArchitectureRules.ROOT + ".architecture.fixture");

        assertThatThrownBy(() -> ArchitectureRules.noPrimitiveMoneyPercentOrGradeFields("..dto..").check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PrimitiveMoneyFixture.amount")
                .hasMessageContaining("never BigDecimal/Double/Float")
                .hasMessageNotContaining("PrimitiveMoneyFixture.quantity");
    }

    @Test
    void percentageDtoFieldsEndWithPercentOrRate() {
        ArchitectureRules.percentageDtoFieldsEndWithPercentOrRate("..dto..").check(MAIN_CLASSES);
    }

    @Test
    void sharedDoesNotDependOnFeatures() {
        FreezingArchRule.freeze(ArchitectureRules.sharedDoesNotDependOnFeatures()).check(MAIN_CLASSES);
    }

    @Test
    void useCaseServicesAreAnnotated() {
        ArchitectureRules.useCaseServicesAreAnnotated().check(MAIN_CLASSES);
    }

    @Test
    void plainServicesAreNotUseCases() {
        ArchitectureRules.plainServicesAreNotUseCases().check(MAIN_CLASSES);
    }
}
