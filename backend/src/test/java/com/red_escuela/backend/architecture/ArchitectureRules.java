package com.red_escuela.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.base.DescribedPredicate;

import com.tngtech.archunit.lang.CompositeArchRule;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceDraft;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDate;
import java.util.Set;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameStartingWith;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

public final class ArchitectureRules {

    public static final String ROOT = "com.red_escuela.backend";

    static final String MONEY_LIKE_NAME =
            "(?i).*(amount|price|fee|debt|balance|total|monto|cuota|matricula|percent|percentage|rate|grade|score|nota|promedio).*";

    static final String[] FEATURE_PACKAGES = {
            ROOT + ".academicrecord..", ROOT + ".academicstructure..", ROOT + ".announcement..",
            ROOT + ".attendance..", ROOT + ".billing..", ROOT + ".enrollment..", ROOT + ".evaluation..",
            ROOT + ".graduation..", ROOT + ".identity..", ROOT + ".notification.."
    };

    static final String[] V2_BILLING_PACKAGES = {"..billing.configuration..", "..billing.operation.."};

    static final String[] V2_BILLING_SUBDOMAINS = {ROOT + ".billing.configuration", ROOT + ".billing.operation"};

    private static final Pattern BILLING_SUBDOMAIN_REPOSITORY = Pattern.compile("com\\.red_escuela\\.backend\\.billing\\.([a-z]+)\\.repository(\\..*)?");

    private static final Set<String> PRIMITIVE_NUMBERS = Set.of(
            BigDecimal.class.getName(), Double.class.getName(), Float.class.getName(), "double", "float");

    private static final DescribedPredicate<JavaField> PRIMITIVE_NUMBER_TYPE =
            DescribedPredicate.describe("of a primitive number type",
                    field -> PRIMITIVE_NUMBERS.contains(field.getRawType().getName()));

    private ArchitectureRules() {
    }

    public static ArchRule noPrimitiveMoneyPercentOrGradeFields(String... packages) {
        return noFields()
                .that().areDeclaredInClassesThat().resideInAnyPackage(packages)
                .and().areDeclaredInClassesThat(DescribedPredicate.describe("not nested", (JavaClass c) -> !c.isNestedClass()))
                .and(PRIMITIVE_NUMBER_TYPE)
                .and().haveNameMatching(MONEY_LIKE_NAME)
                .should().haveNameMatching(".*")
                .because("money must be Money, percentages Percentage and grades Grade (com.red_escuela.backend.shared.domain), never BigDecimal/Double/Float");
    }

    public static ArchRule percentageDtoFieldsEndWithPercentOrRate(String... packages) {
        return fields()
                .that().areDeclaredInClassesThat().resideInAnyPackage(packages)
                .and().haveRawType(ROOT + ".shared.domain.Percentage")
                .should().haveNameMatching("(?i).*(percent|rate)")
                .because("a Percentage exposed in a DTO must be named with a Percent or Rate suffix");
    }

    public static ArchRule sharedDoesNotDependOnFeatures() {
        return noClasses()
                .that().resideInAPackage(ROOT + ".shared..")
                .should().dependOnClassesThat().resideInAnyPackage(FEATURE_PACKAGES)
                .because("shared is the common base and must not depend on any feature");
    }

    public static ArchRule useCaseServicesAreAnnotated() {
        return classes()
                .that().resideInAPackage("..usecase..")
                .and().haveSimpleNameEndingWith("Service")
                .and(DescribedPredicate.describe("top level", (JavaClass c) -> !c.isNestedClass()))
                .should().beAnnotatedWith(ROOT + ".shared.UseCase")
                .because("a *Service in a usecase package is a use case and must carry @UseCase");
    }

    public static ArchRule plainServicesAreNotUseCases() {
        return noClasses()
                .that().resideInAPackage("..service..")
                .should().beAnnotatedWith(ROOT + ".shared.UseCase")
                .because("@UseCase is reserved for classes in a usecase package");
    }

    public static ArchRule invoicesAreIssuedOnlyByChargeIssuer() {
        ArchRule issueCalls = noClasses().that().resideOutsideOfPackages("..billing.issuing..")
                .should().callMethod(Invoice.class, "issue", InvoiceDraft.class, LocalDate.class)
                .because("every invoice must be issued through ChargeIssuer");
        ArchRule constructorCalls = noClasses().that().resideOutsideOfPackages("..billing.entity..")
                .should().callConstructor(Invoice.class)
                .because("every invoice must be issued through ChargeIssuer");
        return CompositeArchRule.of(issueCalls).and(constructorCalls);
    }

    public static ArchRule invoiceSettersAreNotCalledOutsideBillingEntityAndIssuing() {
        return noClasses().that().resideOutsideOfPackages("..billing.entity..", "..billing.issuing..")
                .should().callMethodWhere(target(nameStartingWith("set")).and(target(owner(assignableTo(Invoice.class)))))
                .because("invoice state changes through aggregate methods, not setters");
    }

    public static ArchRule policiesArePure() {
        return noClasses().that().resideInAnyPackage("..policy..", "..billing.planning..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..", "..repository..")
                .because("policies and planners are pure domain logic");
    }

    public static ArchRule businessExceptionsAreThrownOnlyByPolicyEvaluation(String... v2Packages) {
        return noClasses()
                .that().resideInAnyPackage(v2Packages)
                .and().resideOutsideOfPackages("..policy..", "..domain..", ROOT + ".shared.domain.policy..")
                .and().areNotAssignableTo(BadRequestException.class)
                .and().areNotAssignableTo(ConflictException.class)
                .should().callConstructorWhere(target(owner(assignableTo(BadRequestException.class)))
                        .or(target(owner(assignableTo(ConflictException.class)))))
                .allowEmptyShould(true)
                .because("business rejections come out of policy evaluation, never from an if in a use case or service");
    }

    public static ArchRule useCasesDependOnlyOnPortsRepositoriesAndPolicies(String subdomain) {
        return classes()
                .that().resideInAPackage(subdomain + ".usecase..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        subdomain + ".repository..", subdomain + ".policy..", subdomain + ".domain..", subdomain + ".service..",
                        subdomain + ".dto..", subdomain + ".usecase..", ROOT + ".shared..", ROOT + ".billing.operation..",
                        "java..", "org.springframework..", "jakarta.transaction..", "org.slf4j..")
                .allowEmptyShould(true)
                .because("a use case orchestrates ports, repositories and policies of its own subdomain");
    }

    public static ArchRule subdomainsDoNotUseOtherSubdomainRepositories(String subdomain) {
        String name = subdomain.substring(subdomain.lastIndexOf('.') + 1);
        return classes()
                .that().resideInAPackage(subdomain + "..")
                .should(notDependOnRepositoryOfAnotherSubdomain(name))
                .allowEmptyShould(true)
                .because("a billing subdomain reaches another one through its service or port, never through its repository");
    }

    private static ArchCondition<JavaClass> notDependOnRepositoryOfAnotherSubdomain(String ownSubdomain) {
        return new ArchCondition<>("not depend on the repository of another billing subdomain") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    Matcher target = BILLING_SUBDOMAIN_REPOSITORY.matcher(dependency.getTargetClass().getPackageName());
                    if (target.matches() && !target.group(1).equals(ownSubdomain)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    public static ArchRule billingV2EntitiesHaveNoPublicSetters(Class<? extends java.lang.annotation.Annotation> entityAnnotation, String... v2Packages) {
        return noMethods()
                .that().areDeclaredInClassesThat().resideInAnyPackage(v2Packages)
                .and().areDeclaredInClassesThat().areAnnotatedWith(entityAnnotation)
                .and().arePublic()
                .and().haveNameMatching("set[A-Z].*")
                .should().haveNameMatching(".*")
                .allowEmptyShould(true)
                .because("entities change state through intention-revealing methods, not setters");
    }

    public static ArchRule everyPolicyDeclaresItsRule() {
        return classes()
                .that().implement(Policy.class)
                .and().areNotInterfaces()
                .and().areNotAnonymousClasses()
                .should(declareViolationCodesAndDecisions())
                .allowEmptyShould(true)
                .because("the live catalog needs each policy's violation codes and decisions");
    }

    private static ArchCondition<JavaClass> declareViolationCodesAndDecisions() {
        return new ArchCondition<>("be annotated with @PolicyRule with at least one violation code and one decision") {
            @Override
            public void check(JavaClass policy, ConditionEvents events) {
                if (!policy.isAnnotatedWith(PolicyRule.class)) {
                    events.add(SimpleConditionEvent.violated(policy, policy.getName() + " is not annotated with @PolicyRule"));
                    return;
                }
                JavaAnnotation<JavaClass> rule = policy.getAnnotationOfType(PolicyRule.class.getName());
                if (isEmpty(rule, "violationCodes") || isEmpty(rule, "decisions")) {
                    events.add(SimpleConditionEvent.violated(policy, policy.getName() + " has an empty @PolicyRule"));
                }
            }

            private boolean isEmpty(JavaAnnotation<JavaClass> rule, String property) {
                return rule.get(property).map(value -> ((Object[]) value).length == 0).orElse(true);
            }
        };
    }

    public static ArchRule academicStructureDoesNotDependOnBilling() {
        return noClasses()
                .that().resideInAPackage(ROOT + ".academicstructure..")
                .should().dependOnClassesThat().resideInAPackage("..billing..")
                .because("billing plugs into academic structure through ports declared there");
    }

    public static ArchRule persistedBillingAmountsAreNonNegative(Class<? extends java.lang.annotation.Annotation> entityAnnotation, String... v2Packages) {
        return noFields()
                .that().areDeclaredInClassesThat().resideInAnyPackage(v2Packages)
                .and().areDeclaredInClassesThat().areAnnotatedWith(entityAnnotation)
                .and().haveRawType(Money.class)
                .should().haveNameMatching(".*")
                .allowEmptyShould(true)
                .because("persisted billing amounts are NonNegativeMoney (D-171)");
    }
}
