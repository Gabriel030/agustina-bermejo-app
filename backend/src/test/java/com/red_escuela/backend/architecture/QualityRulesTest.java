package com.red_escuela.backend.architecture;

import com.red_escuela.backend.shared.time.RawNowRule;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QualityRulesTest {

    private static final JavaClasses MAIN_CLASSES = RawNowRule.importMainClasses();

    private static final String FIXTURE_ROOT = ArchitectureRules.ROOT + ".architecture.fixture.quality";

    private static JavaClasses fixtureClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages(FIXTURE_ROOT);
    }

    @Test
    void transactionsAreDeclaredOnlyInServicesAndUseCases() {
        FreezingArchRule.freeze(QualityRules.transactionsAreDeclaredOnlyInServicesAndUseCases()).check(MAIN_CLASSES);
    }

    @Test
    void transactionRuleDetectsATransactionalControllerAndAllowsAService() {
        assertThatThrownBy(() -> QualityRules.transactionsAreDeclaredOnlyInServicesAndUseCases().check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("TransactionalControllerFixture")
                .hasMessageNotContaining("TransactionalServiceFixture");
    }

    @Test
    void eventListenersDoNotDeclareTheirOwnTransaction() {
        FreezingArchRule.freeze(QualityRules.eventListenersDoNotDeclareTheirOwnTransaction()).check(MAIN_CLASSES);
    }

    @Test
    void listenerRuleDetectsATransactionalListenerAndAllowsAPlainOne() {
        assertThatThrownBy(() -> QualityRules.eventListenersDoNotDeclareTheirOwnTransaction().check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("TransactionalListenerFixture")
                .hasMessageNotContaining("PlainListenerFixture");
    }

    @Test
    void featureSubpackagesAreFreeOfCycles() {
        for (String feature : QualityRules.FEATURES) {
            Set<String> only = feature.equals("billing") ? QualityRules.BILLING_SUBDOMAINS : Set.of();
            FreezingArchRule.freeze(QualityRules.subpackagesAreFreeOfCycles(ArchitectureRules.ROOT + "." + feature, only)).check(MAIN_CLASSES);
        }
    }

    @Test
    void cycleRuleDetectsTwoSubpackagesDependingOnEachOther() {
        assertThatThrownBy(() -> QualityRules.subpackagesAreFreeOfCycles(FIXTURE_ROOT + ".cycle").check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("FirstCycleFixture")
                .hasMessageContaining("SecondCycleFixture");
    }

    @Test
    void externalClientsAreUsedOnlyFromAdaptersAndInfrastructure() {
        FreezingArchRule.freeze(QualityRules.externalClientsAreUsedOnlyFromAdaptersAndInfrastructure()).check(MAIN_CLASSES);
    }

    @Test
    void externalClientRuleDetectsAStrayClientAndAllowsAnAdapter() {
        assertThatThrownBy(() -> QualityRules.externalClientsAreUsedOnlyFromAdaptersAndInfrastructure().check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("StrayRestClientFixture")
                .hasMessageNotContaining("AdapterRestClientFixture");
    }

    @Test
    void useCasesOfEveryFeatureDependOnlyOnPortsRepositoriesAndPolicies() {
        for (String feature : QualityRules.FEATURES) {
            FreezingArchRule.freeze(ArchitectureRules.useCasesDependOnlyOnPortsRepositoriesAndPolicies(ArchitectureRules.ROOT + "." + feature))
                    .check(MAIN_CLASSES);
        }
    }
}
