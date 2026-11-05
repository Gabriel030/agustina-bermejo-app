package com.red_escuela.backend.architecture;

import com.red_escuela.backend.shared.time.RawNowRule;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.base.DescribedPredicate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

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
    void theIdentityApiSignaturesExposeNoRepository() {
        ModulePatternRules.apiSignaturesExposeNoRepository(ROOT + ".identity.api..").check(MAIN_CLASSES);
    }

    @Test
    @Tag("e03")
    void reportingReadsBillingOnlyThroughItsApiAndEvents() {
        DescribedPredicate<JavaClass> billingInternals = resideInAPackage(ROOT + ".billing..")
                .and(DescribedPredicate.not(resideInAnyPackage(ROOT + ".billing.api..", ROOT + ".billing.events..")));

        noClasses().that().resideInAPackage(ROOT + ".notification.reporting..")
                .should().dependOnClassesThat(billingInternals)
                .check(MAIN_CLASSES);
    }

    @Test
    @Tag("e03")
    void billingNeverDependsOnTheNotificationDeliveryOrReportingInternals() {
        noClasses().that().resideInAPackage(ROOT + ".billing..")
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".notification.delivery..", ROOT + ".notification.reporting..")
                .check(MAIN_CLASSES);
    }

    @Test
    @Tag("e03")
    void billingSourcesNeverMentionTheNotificationDeliveryTable() throws IOException {
        List<Path> offenders;
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java/com/red_escuela/backend/billing"))) {
            offenders = sources.filter(path -> path.toString().endsWith(".java")).filter(NotificationDeliveryBoundaryTest::mentionsDeliveryTable).toList();
        }

        assertThat(offenders).isEmpty();
    }

    private static boolean mentionsDeliveryTable(Path source) {
        try {
            return Files.readString(source).contains("notification_delivery");
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
