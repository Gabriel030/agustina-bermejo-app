package com.red_escuela.backend.architecture;

import com.red_escuela.backend.billing.authorization.service.ExpireAuthorizationRequestsJob;
import com.red_escuela.backend.billing.configuration.service.ActivateDueConfigurationVersionsJob;
import com.red_escuela.backend.billing.service.EnrollmentBillingComplianceSweepService;
import com.red_escuela.backend.billing.service.InvoiceOverdueSweepService;
import com.red_escuela.backend.billing.run.operation.FailedInvoiceRetryJob;
import com.red_escuela.backend.billing.run.operation.MonthlyInvoiceIssuanceJob;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.event.EventPublicationResubmissionJob;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.red_escuela.backend.testsupport.ImportedApplicationClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

final class TestSuiteRules {

    static final String FIXTURE_PACKAGE = ArchitectureRules.ROOT + ".architecture.fixture";

    private static final Set<Class<?>> GLOBAL_PROCESSES = Set.of(
            InvoiceOverdueSweepService.class,
            EnrollmentBillingComplianceSweepService.class,
            MonthlyInvoiceIssuanceJob.class,
            FailedInvoiceRetryJob.class,
            ExpireAuthorizationRequestsJob.class,
            ActivateDueConfigurationVersionsJob.class,
            EventPublicationResubmissionJob.class);

    private TestSuiteRules() {
    }

    static JavaClasses importTestClasses() {
        return ImportedApplicationClasses.testsOutsideFixtures();
    }

    static JavaClasses importFixtureClasses() {
        return ImportedApplicationClasses.fixturesIn(FIXTURE_PACKAGE);
    }

    static ArchRule domainAndPolicyTestsDoNotBootSpring() {
        return noClasses()
                .that().resideInAnyPackage("..domain..", "..policy..")
                .should().beMetaAnnotatedWith(SpringBootTest.class)
                .orShould().beAssignableTo(AbstractIntegrationTest.class)
                .as("Test classes in domain and policy packages must be pure unit tests: no @SpringBootTest and no AbstractIntegrationTest")
                .allowEmptyShould(true);
    }

    static ArchRule testsDoNotSleep() {
        return noClasses()
                .should().callMethod(Thread.class, "sleep", long.class)
                .orShould().callMethod(Thread.class, "sleep", long.class, int.class)
                .orShould().callMethod(Thread.class, "sleep", java.time.Duration.class)
                .as("Tests must not call Thread.sleep: use Awaitility")
                .allowEmptyShould(true);
    }

    static ArchRule testsDoNotDirtyTheContext() {
        return CompositeArchRule
                .of(noClasses().should().beMetaAnnotatedWith(DirtiesContext.class).allowEmptyShould(true))
                .and(noMethods().should().beMetaAnnotatedWith(DirtiesContext.class).allowEmptyShould(true))
                .as("Tests must not use @DirtiesContext: it throws away a cached Spring context");
    }

    static ArchRule testsDoNotRunGlobalProcessesUnscoped() {
        return noClasses()
                .should().callMethodWhere(DescribedPredicate.describe(
                        "target a global scheduled process (overdue sweep, compliance sweep, monthly issuance, authorization expirer, configuration activation, event resubmission)",
                        (JavaMethodCall call) -> isGlobalProcessEntryPoint(call)))
                .as("Tests must scope global processes: call a use case or step with explicit ids instead of a job that scans the whole database")
                .allowEmptyShould(true);
    }

    private static boolean isGlobalProcessEntryPoint(JavaMethodCall call) {
        JavaClass owner = call.getTargetOwner();
        boolean globalOwner = GLOBAL_PROCESSES.stream().anyMatch(type -> owner.isEquivalentTo(type));
        return globalOwner && !call.getTarget().getName().equals("<init>");
    }
}
