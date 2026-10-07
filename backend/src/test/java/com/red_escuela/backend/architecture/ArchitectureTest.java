package com.red_escuela.backend.architecture;

import com.red_escuela.backend.shared.time.RawNowRule;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import com.red_escuela.backend.architecture.fixture.billing.configuration.entity.FixtureEntity;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Tag;
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

    @Test
    @Tag("D-051")
    void invoicesAreIssuedOnlyByChargeIssuer() {
        ArchitectureRules.invoicesAreIssuedOnlyByChargeIssuer().check(MAIN_CLASSES);
    }

    @Test
    void invoiceSettersAreNotCalledOutsideBillingEntityAndIssuing() {
        ArchitectureRules.invoiceSettersAreNotCalledOutsideBillingEntityAndIssuing().check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-051")
    void policiesAndPlannersArePure() {
        ArchitectureRules.policiesArePure().check(MAIN_CLASSES);
    }

    @Test
    void issuanceRulesDetectViolations() {
        JavaClasses fixture = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages(ArchitectureRules.ROOT + ".architecture.fixture");

        assertThatThrownBy(() -> ArchitectureRules.invoicesAreIssuedOnlyByChargeIssuer().check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("InvoiceIssuingFixture");
        assertThatThrownBy(() -> ArchitectureRules.invoiceSettersAreNotCalledOutsideBillingEntityAndIssuing().check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("InvoiceIssuingFixture");
        assertThatThrownBy(() -> ArchitectureRules.policiesArePure().check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ImpurePolicyFixture");
    }

    private static final String FIXTURE_V2 = ArchitectureRules.ROOT + ".architecture.fixture.billing.configuration";

    private static JavaClasses fixtureClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages(ArchitectureRules.ROOT + ".architecture.fixture");
    }

    @Test
    @Tag("D-051")
    void businessExceptionsAreThrownOnlyByPolicyEvaluation() {
        ArchitectureRules.businessExceptionsAreThrownOnlyByPolicyEvaluation(ArchitectureRules.V2_BILLING_PACKAGES).check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-051")
    void businessExceptionsRuleDetectsAThrowOutsideAPolicy() {
        assertThatThrownBy(() -> ArchitectureRules.businessExceptionsAreThrownOnlyByPolicyEvaluation(FIXTURE_V2 + "..").check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ServiceThrowingABusinessExceptionFixture");
    }

    @Test
    @Tag("D-051")
    void useCasesDependOnlyOnPortsRepositoriesAndPolicies() {
        for (String subdomain : ArchitectureRules.V2_BILLING_SUBDOMAINS) {
            ArchitectureRules.useCasesDependOnlyOnPortsRepositoriesAndPolicies(subdomain).check(MAIN_CLASSES);
        }
    }

    @Test
    @Tag("D-051")
    void useCaseDependencyRuleDetectsAnEntityDependency() {
        assertThatThrownBy(() -> ArchitectureRules.useCasesDependOnlyOnPortsRepositoriesAndPolicies(FIXTURE_V2).check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UseCaseDependingOnAnEntityFixture");
    }

    @Test
    @Tag("D-051")
    @Tag("D-173")
    void subdomainsDoNotUseOtherSubdomainRepositories() {
        for (String subdomain : ArchitectureRules.V2_BILLING_SUBDOMAINS) {
            ArchitectureRules.subdomainsDoNotUseOtherSubdomainRepositories(subdomain).check(MAIN_CLASSES);
        }
    }

    @Test
    @Tag("D-051")
    @Tag("D-173")
    void subdomainRepositoryRuleDetectsTheDependencyAndAllowsItsOwnRepository() {
        assertThatThrownBy(() -> ArchitectureRules.subdomainsDoNotUseOtherSubdomainRepositories(FIXTURE_V2).check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ServiceUsingAnotherSubdomainRepositoryFixture")
                .hasMessageNotContaining("ServiceThrowingABusinessExceptionFixture");
    }

    @Test
    @Tag("D-051")
    void billingV2EntitiesHaveNoPublicSetters() {
        ArchitectureRules.billingV2EntitiesHaveNoPublicSetters(Entity.class, ArchitectureRules.V2_BILLING_PACKAGES).check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-051")
    void entitySetterRuleDetectsASetterAndIgnoresCleanEntities() {
        assertThatThrownBy(() -> ArchitectureRules.billingV2EntitiesHaveNoPublicSetters(FixtureEntity.class, FIXTURE_V2 + "..").check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("EntityWithSetterAndMoneyFixture.setId")
                .hasMessageNotContaining("CleanEntityFixture");
    }

    @Test
    @Tag("D-051")
    void everyPolicyDeclaresItsRule() {
        ArchitectureRules.everyPolicyDeclaresItsRule().check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-051")
    void policyRuleDetectsAMissingOrEmptyDeclaration() {
        assertThatThrownBy(() -> ArchitectureRules.everyPolicyDeclaresItsRule().check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PolicyWithoutRuleFixture is not annotated with @PolicyRule")
                .hasMessageContaining("PolicyWithEmptyRuleFixture has an empty @PolicyRule");
    }

    @Test
    @Tag("D-051")
    void policiesInTheConfigurationSubdomainArePure() {
        assertThatThrownBy(() -> ArchitectureRules.policiesArePure().check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("SpringDependentPolicyFixture");
    }

    @Test
    @Tag("D-051")
    void academicStructureDoesNotDependOnBilling() {
        FreezingArchRule.freeze(ArchitectureRules.academicStructureDoesNotDependOnBilling()).check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-051")
    void academicStructureRuleDetectsABillingDependency() {
        JavaClasses academicStructureToBilling = new ClassFileImporter()
                .importClasses(com.red_escuela.backend.academicstructure.service.ProgramService.class);

        assertThatThrownBy(() -> ArchitectureRules.academicStructureDoesNotDependOnBilling().check(academicStructureToBilling))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ProgramService");
    }

    @Test
    @Tag("D-051")
    @Tag("D-171")
    void persistedBillingAmountsAreNonNegative() {
        ArchitectureRules.persistedBillingAmountsAreNonNegative(Entity.class, ArchitectureRules.V2_BILLING_PACKAGES).check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-051")
    @Tag("D-171")
    void moneyFieldRuleDetectsAnEntityWithPlainMoney() {
        assertThatThrownBy(() -> ArchitectureRules.persistedBillingAmountsAreNonNegative(FixtureEntity.class, FIXTURE_V2 + "..").check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("EntityWithSetterAndMoneyFixture.amount")
                .hasMessageNotContaining("CleanEntityFixture");
    }
}
