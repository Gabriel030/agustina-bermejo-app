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
    void invoicesAreIssuedOnlyByInvoiceIssuingService() {
        ArchitectureRules.invoicesAreIssuedOnlyByInvoiceIssuingService().check(MAIN_CLASSES);
    }

    @Test
    @Tag("B2-33")
    @Tag("D-127")
    void invoicesAreCancelledOnlyThroughTheCancellationOperation() {
        ArchitectureRules.invoicesAreCancelledOnlyThroughTheCancellationOperation().check(MAIN_CLASSES);
    }

    @Test
    @Tag("B2-33")
    @Tag("D-127")
    void cancellationRuleDetectsABackdoor() {
        assertThatThrownBy(() -> ArchitectureRules.invoicesAreCancelledOnlyThroughTheCancellationOperation().check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("CancellationBackdoorFixture");
    }

    @Test
    void invoicePaymentAllocationIsCalledOnlyByTheInvoiceAndPaymentPackages() {
        ArchitectureRules.invoicePaymentAllocationIsCalledOnlyByTheInvoiceAndPaymentPackages().check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-051")
    void monthlyInvoicePlannersDependOnlyOnPlanningPoliciesAndDomain() {
        ArchitectureRules.monthlyInvoicePlannersDependOnlyOnPlanningPoliciesAndDomain().check(MAIN_CLASSES);
        assertThatThrownBy(() -> ArchitectureRules.monthlyInvoicePlannersDependOnlyOnPlanningPoliciesAndDomain().check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("LoggingFixtureMonthlyInvoicePlanner");
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

        assertThatThrownBy(() -> ArchitectureRules.invoicesAreIssuedOnlyByInvoiceIssuingService().check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("InvoiceIssuingFixture");
        assertThatThrownBy(() -> ArchitectureRules.invoicePaymentAllocationIsCalledOnlyByTheInvoiceAndPaymentPackages().check(fixture))
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
    @Tag("D-178")
    @Tag("D-183")
    void asyncProcessingIsNotUsedInEnrollmentAndOnlyInBillingPostListeners() {
        ArchitectureRules.asyncProcessingIsNotUsedInEnrollmentAndOnlyInBillingPostListeners(ArchitectureRules.ROOT + ".enrollment", ArchitectureRules.ROOT + ".billing")
                .check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-178")
    @Tag("D-183")
    void asyncRuleDetectsEnrollmentAndNonListenerBillingClasses() {
        String fixtureRoot = ArchitectureRules.ROOT + ".architecture.fixture";

        assertThatThrownBy(() -> ArchitectureRules.asyncProcessingIsNotUsedInEnrollmentAndOnlyInBillingPostListeners(fixtureRoot + ".enrollment", fixtureRoot + ".billing.operation")
                .check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("AsyncEnrollmentFixture")
                .hasMessageContaining("AsyncBillingServiceFixture")
                .hasMessageNotContaining("AllowedPostListener");
    }

    @Test
    @Tag("D-178")
    void domainEventsCarryNoEntities() {
        ArchitectureRules.domainEventsCarryNoEntities(Entity.class).check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-178")
    void domainEventRuleDetectsEntityFields() {
        assertThatThrownBy(() -> ArchitectureRules.domainEventsCarryNoEntities(FixtureEntity.class).check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("EntityCarryingEventFixture")
                .hasMessageNotContaining("FlatEventFixture");
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

    private static final String INVOICE_CLASS = "com.red_escuela.backend.billing.invoice.domain.Invoice";
    private static final String INVOICE_FACTORY_CLASS = "com.red_escuela.backend.billing.invoice.domain.InvoiceFactory";
    private static final String DOCUMENT_MACHINE_CLASS = "com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatusStateMachine";
    private static final String PAYMENT_MACHINE_CLASS = "com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatusStateMachine";
    private static final String STATUS_FIXTURE_CLASS = "com.red_escuela.backend.architecture.fixture.billing.invoice.StatusAggregateFixture";

    @Test
    @Tag("D-051")
    void invoicesAreCreatedOnlyByInvoiceFactory() {
        ArchitectureRules.invoicesAreCreatedOnlyByInvoiceFactory(INVOICE_CLASS, INVOICE_FACTORY_CLASS).check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-051")
    void invoiceCreationRuleDetectsADraftCreatedOutsideTheFactory() {
        JavaClasses fixture = new ClassFileImporter()
                .importClasses(com.red_escuela.backend.billing.invoice.domain.InvoiceDraftCreatingFixture.class);

        assertThatThrownBy(() -> ArchitectureRules.invoicesAreCreatedOnlyByInvoiceFactory(INVOICE_CLASS, INVOICE_FACTORY_CLASS).check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("InvoiceDraftCreatingFixture");
    }

    @Test
    @Tag("D-051")
    void statusChangesOnlyThroughTransitionTables() {
        ArchitectureRules.statusFieldIsWrittenOnlyThroughItsTransitionTable(INVOICE_CLASS, "documentStatus", "changeDocumentStatus", DOCUMENT_MACHINE_CLASS).check(MAIN_CLASSES);
        ArchitectureRules.statusFieldIsWrittenOnlyThroughItsTransitionTable(INVOICE_CLASS, "paymentStatus", "changePaymentStatus", PAYMENT_MACHINE_CLASS).check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-051")
    void transitionTableRuleDetectsAWriteOutsideTheTransitionMethodAndAMethodThatSkipsTheMachine() {
        assertThatThrownBy(() -> ArchitectureRules.statusFieldIsWrittenOnlyThroughItsTransitionTable(
                STATUS_FIXTURE_CLASS, "status", "changeStatus", DOCUMENT_MACHINE_CLASS).check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("rogueWrite")
                .hasMessageContaining("StatusAggregateFixture.changeStatus does not call")
                .hasMessageNotContaining("StatusAggregateFixture.changeStatus()");
    }

    @Test
    @Tag("D-051")
    void billingV2DoesNotDependOnLegacyBilling() {
        ArchitectureRules.billingV2DoesNotDependOnLegacyBilling("..billing.invoice..", "..billing.account..", "..billing.note..").check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-051")
    void legacyDependencyRuleDetectsAV2ClassUsingLegacyBilling() {
        assertThatThrownBy(() -> ArchitectureRules.billingV2DoesNotDependOnLegacyBilling(ArchitectureRules.ROOT + ".architecture.fixture.billing.invoice..").check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("LegacyBillingDependingFixture");
    }

    private static final String ENTRY_CLASS = "com.red_escuela.backend.billing.account.domain.CurrentAccountEntry";
    private static final String ENTRY_REPOSITORY_CLASS = "com.red_escuela.backend.billing.account.repository.CurrentAccountEntryRepository";
    private static final String ACCOUNT_REPOSITORY_CLASS = "com.red_escuela.backend.billing.account.repository.StudentCurrentAccountRepository";
    private static final String ACCOUNT_SERVICE_CLASS = "com.red_escuela.backend.billing.account.service.StudentCurrentAccountService";
    private static final String INVOICE_REPOSITORY_CLASS = "com.red_escuela.backend.billing.invoice.repository.InvoiceRepository";

    @Test
    @Tag("D-094")
    @Tag("D-051")
    void paymentAllocationsAreAppendOnly() {
        ArchitectureRules.paymentAllocationsAreAppendOnly("com.red_escuela.backend.billing.account.domain.PaymentAllocation",
                "com.red_escuela.backend.billing.account.repository.PaymentAllocationRepository").check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-144")
    void currentAccountEntriesAreAppendOnly() {
        ArchitectureRules.currentAccountEntriesAreAppendOnly(ENTRY_CLASS, ENTRY_REPOSITORY_CLASS, ACCOUNT_REPOSITORY_CLASS, "insertStudentCurrentAccountIfAbsent").check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-144")
    void appendOnlyRuleDetectsMutationAndDeletion() {
        assertThatThrownBy(() -> ArchitectureRules.currentAccountEntriesAreAppendOnly(
                FIXTURE_LEDGER + ".MutableEntryFixture", FIXTURE_LEDGER + ".DeletingEntryRepositoryFixture", FIXTURE_LEDGER + ".ModifyingAccountRepositoryFixture",
                "insertStudentCurrentAccountIfAbsent").check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("MutableEntryFixture.changeConcept")
                .hasMessageContaining("DeletingEntryRepositoryFixture.deleteById")
                .hasMessageContaining("ModifyingAccountRepositoryFixture.resetBalance")
                .hasMessageNotContaining("getConcept")
                .hasMessageNotContaining("insertStudentCurrentAccountIfAbsent");
    }

    @Test
    @Tag("D-144")
    void economicStateIsPersistedOnlyByOperations() {
        ArchitectureRules.economicStateIsPersistedOnlyByOperations(INVOICE_REPOSITORY_CLASS, com.red_escuela.backend.billing.operation.BillingOperation.class).check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-144")
    void currentAccountIsWrittenOnlyThroughItsService() {
        ArchitectureRules.currentAccountIsWrittenOnlyThroughItsService(ACCOUNT_SERVICE_CLASS, ACCOUNT_REPOSITORY_CLASS, ENTRY_REPOSITORY_CLASS).check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-144")
    void currentAccountServiceIsCalledOnlyByOperations() {
        ArchitectureRules.currentAccountServiceIsCalledOnlyByOperations(ACCOUNT_SERVICE_CLASS, com.red_escuela.backend.billing.operation.BillingOperation.class).check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-144")
    void writeRulesDetectCallersThatAreNotOperations() {
        assertThatThrownBy(() -> ArchitectureRules.economicStateIsPersistedOnlyByOperations(INVOICE_REPOSITORY_CLASS, com.red_escuela.backend.architecture.fixture.billing.ledger.FixtureOperation.class).check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UnauthorizedInvoiceSaverFixture")
                .hasMessageNotContaining("AuthorizedOperationFixture");
        assertThatThrownBy(() -> ArchitectureRules.currentAccountIsWrittenOnlyThroughItsService(ACCOUNT_SERVICE_CLASS, ACCOUNT_REPOSITORY_CLASS, ENTRY_REPOSITORY_CLASS).check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UnauthorizedAccountWriterFixture")
                .hasMessageNotContaining("AuthorizedOperationFixture");
        assertThatThrownBy(() -> ArchitectureRules.currentAccountServiceIsCalledOnlyByOperations(ACCOUNT_SERVICE_CLASS, com.red_escuela.backend.architecture.fixture.billing.ledger.FixtureOperation.class).check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UnauthorizedRecorderFixture")
                .hasMessageNotContaining("AuthorizedOperationFixture");
    }

    @Test
    @Tag("D-144")
    void theAccountServiceRuleAlsoCoversTheAllocationOfCredit() {
        assertThatThrownBy(() -> ArchitectureRules.currentAccountServiceIsCalledOnlyByOperations(ACCOUNT_SERVICE_CLASS, com.red_escuela.backend.architecture.fixture.billing.ledger.FixtureOperation.class).check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UnauthorizedAllocatorFixture.allocate");
    }

    @Test
    @Tag("D-144")
    void thePaymentBridgeExemptionCoversOnlyItsOwnMethods() {
        String bridge = "com.red_escuela.backend.architecture.fixture.billing.ledger.BridgeOverreachFixture";
        assertThatThrownBy(() -> ArchitectureRules.methodIsCalledOnlyBy(ACCOUNT_SERVICE_CLASS, name -> name.startsWith("record"), ACCOUNT_SERVICE_CLASS,
                com.red_escuela.backend.architecture.fixture.billing.ledger.FixtureOperation.class, "recording of an account entry",
                bridge, java.util.Set.of("recordPaymentReceivedCredit")).check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("BridgeOverreachFixture.recordWhatTheBridgeMayNotRecord")
                .hasMessageNotContaining("BridgeOverreachFixture.recordWhatTheBridgeMayRecord");
    }

    private static final String FIXTURE_LEDGER = ArchitectureRules.ROOT + ".architecture.fixture.billing.ledger";

    private static java.util.Set<String> frozenLegacyBillingClasses() throws java.io.IOException {
        try (var in = ArchitectureTest.class.getResourceAsStream("/billing-legacy-classes.txt")) {
            return new java.util.HashSet<>(java.util.Arrays.asList(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")));
        }
    }

    @Test
    @Tag("D-173")
    void billingClassesLiveInTheirSubdomainFolders() throws java.io.IOException {
        ArchitectureRules.billingClassesLiveInTheirSubdomainFolders(ArchitectureRules.ROOT + ".billing", frozenLegacyBillingClasses()).check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-173")
    void subdomainFoldersRuleDetectsAMisplacedClassAndANewLegacyClass() {
        assertThatThrownBy(() -> ArchitectureRules.billingClassesLiveInTheirSubdomainFolders(ArchitectureRules.ROOT + ".architecture.fixture.billing", java.util.Set.of()).check(fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("MisplacedBillingClassFixture")
                .hasMessageContaining("NewLegacyEntityFixture");
    }
}
