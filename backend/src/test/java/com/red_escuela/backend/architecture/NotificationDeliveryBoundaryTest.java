package com.red_escuela.backend.architecture;

import com.red_escuela.backend.shared.time.RawNowRule;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@Tag("c11")
class NotificationDeliveryBoundaryTest {

    private static final JavaClasses MAIN_CLASSES = RawNowRule.importMainClasses();
    private static final String ROOT = ArchitectureRules.ROOT;

    @Test
    void notificationNeverOpensASmtpConnectionOrUsesTheVerificationMailer() {
        noClasses().that().resideInAPackage(ROOT + ".notification..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.mail..", "jakarta.mail..")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(ROOT + ".shared.mail.BrevoEmailService")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(ROOT + ".shared.mail.EmailService")
                .check(MAIN_CLASSES);
    }

    @Test
    void theSimulatedGatewayDoesNotTouchTheRealMailers() {
        noClasses().that().haveSimpleNameContaining("TransactionalEmail")
                .or().haveSimpleName("MaskedEmailAddress")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.mail..", "jakarta.mail..")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(ROOT + ".shared.mail.BrevoEmailService")
                .check(MAIN_CLASSES);
    }

    @Test
    void deliveryReachesIdentityOnlyThroughItsApi() {
        noClasses().that().resideInAPackage(ROOT + ".notification.delivery..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".identity.repository..", ROOT + ".identity.service..", ROOT + ".identity.usecase..", ROOT + ".identity.controller..")
                .check(MAIN_CLASSES);
    }

    @Test
    void deliveryDoesNotReachBillingInternals() {
        noClasses().that().resideInAPackage(ROOT + ".notification.delivery..")
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".billing..")
                .check(MAIN_CLASSES);
    }

    @Test
    void theIdentityApiDoesNotExposeEntitiesOrRepositories() {
        noClasses().that().resideInAPackage(ROOT + ".identity.api..")
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".identity.entity..", ROOT + ".identity.repository..")
                .check(MAIN_CLASSES);
    }
}
