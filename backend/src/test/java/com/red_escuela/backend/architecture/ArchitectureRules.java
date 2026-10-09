package com.red_escuela.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.base.DescribedPredicate;

import com.tngtech.archunit.lang.CompositeArchRule;
import com.red_escuela.backend.billing.invoice.domain.Invoice;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
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
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
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

    static final String[] V2_BILLING_PACKAGES = {"..billing.configuration..", "..billing.operation..", "..billing.authorization..", "..billing.invoice..", "..billing.account.."};

    static final String[] V2_BILLING_SUBDOMAINS = {ROOT + ".billing.configuration", ROOT + ".billing.operation", ROOT + ".billing.authorization", ROOT + ".billing.invoice", ROOT + ".billing.account"};

    static final String LEGACY_COUPLED_ISSUING = ROOT.replace(".", "\\.") + "\\.billing\\.invoice\\.(?:InvoicePolicySetsConfiguration"
            + "|service\\.legacy\\.[A-Za-z]+"
            + "|service\\.(?:InvoiceIssuingRequestFactory|ProgramAdmissionInvoiceIssuer|InvoiceView|InvoiceViewLoader|BankTransferReceiptResponseAssembler))(?:\\$.*)?";

    static final String PAYMENT_BRIDGE_UNTIL_PAYMENT_OPERATION = ROOT + ".billing.usecase.payment.RegisterPaymentService"
            + "," + ROOT + ".billing.account.service.CurrentAccountAllocationService";

    static final Set<String> PAYMENT_BRIDGE_ACCOUNT_METHODS = Set.of(
            "recordPaymentReceivedCredit", "recordCreditNoteIssuedCredit", "consumeUnallocatedCreditForAllocation", "restoreUnallocatedCreditFromReversedAllocation");

    static final Set<String> BILLING_LEGACY_FLAT_PACKAGES = Set.of("entity", "dto", "service", "usecase", "exception", "controller", "repository", "infrastructure");

    static final Set<String> ENROLLMENT_POST_LISTENERS = Set.of("ProgramEnrollmentAdmissionPaymentListener");

    static final Set<String> BILLING_SUBDOMAIN_FOLDERS = Set.of("domain", "policy", "operation", "usecase", "service", "repository", "controller", "dto", "exception");

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

    public static ArchRule invoicesAreIssuedOnlyByInvoiceIssuingService() {
        return noClasses().that().resideOutsideOfPackages("..billing.invoice.operation..", "..billing.invoice.domain..")
                .should().callMethodWhere(target(name("issueInvoice")).and(target(owner(assignableTo(Invoice.class)))))
                .because("every invoice must be issued through the ISSUE_INVOICE operation");
    }

    public static ArchRule invoicePaymentAllocationIsCalledOnlyByTheInvoiceAndPaymentPackages() {
        return noClasses().that().resideOutsideOfPackages("..billing.invoice..", "..billing.usecase.payment..")
                .and().doNotHaveFullyQualifiedName(ROOT + ".billing.account.service.CurrentAccountAllocationService")
                .should().callMethodWhere(target(name("allocateFullInvoicePayment")).and(target(owner(assignableTo(Invoice.class)))))
                .because("a payment allocation must be registered together with its credit in the current account");
    }

    public static ArchRule policiesArePure() {
        return noClasses().that().resideInAnyPackage("..policy..", "..billing.planning..", "..statemachine..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..", "..repository..")
                .because("policies and planners are pure domain logic");
    }

    public static ArchRule monthlyInvoicePlannersDependOnlyOnPlanningPoliciesAndDomain() {
        return classes().that().haveSimpleNameEndingWith("MonthlyInvoicePlanner").and().areNotInterfaces()
                .should().onlyDependOnClassesThat().resideInAnyPackage("..billing.planning..", "..billing.invoice.domain..", "..billing.entity..",
                        "..shared.domain..", "java..")
                .allowEmptyShould(true)
                .because("planners only orchestrate policies over planning facts");
    }

    public static ArchRule businessExceptionsAreThrownOnlyByPolicyEvaluation(String... v2Packages) {
        return noClasses()
                .that().resideInAnyPackage(v2Packages)
                .and().haveNameNotMatching(LEGACY_COUPLED_ISSUING)
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

    public static ArchRule asyncProcessingIsNotUsedInEnrollmentAndOnlyInBillingPostListeners(String enrollmentPackage, String billingPackage) {
        ArchCondition<com.tngtech.archunit.core.domain.JavaMethod> allowedOwner = new ArchCondition<>("be declared outside enrollment and, in billing, only in a post listener") {
            @Override
            public void check(com.tngtech.archunit.core.domain.JavaMethod method, ConditionEvents events) {
                JavaClass owner = method.getOwner();
                boolean inEnrollment = owner.getPackageName().startsWith(enrollmentPackage);
                boolean inBilling = owner.getPackageName().startsWith(billingPackage);
                boolean enrollmentPostListener = inEnrollment && ENROLLMENT_POST_LISTENERS.contains(owner.getSimpleName());
                boolean allowed = enrollmentPostListener || (!inEnrollment && !(inBilling && !owner.getSimpleName().endsWith("Listener")));
                events.add(new SimpleConditionEvent(method, allowed, method.getFullName() + " is async processing in a forbidden place"));
            }
        };
        return com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods().that().areAnnotatedWith(org.springframework.modulith.events.ApplicationModuleListener.class)
                .or().areAnnotatedWith(org.springframework.scheduling.annotation.Async.class).should(allowedOwner)
                .because("payment and allocation are processed synchronously; async only for idempotent post-commit listeners, including the enrollment derived from the admission payment (D-231, D-178, D-183)");
    }

    public static ArchRule domainEventsCarryNoEntities(Class<? extends java.lang.annotation.Annotation> entityAnnotation) {
        return noFields()
                .that().areDeclaredInClassesThat().areAssignableTo(com.red_escuela.backend.shared.event.DomainEvent.class)
                .should().haveRawType(DescribedPredicate.describe("a JPA entity", (JavaClass type) -> type.isAnnotatedWith(entityAnnotation)))
                .allowEmptyShould(true)
                .because("domain events are serialized into event_publication and carry ids and plain values, never entities");
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
    public static ArchRule invoicesAreCreatedOnlyByInvoiceFactory(String invoiceClassName, String factoryClassName) {
        return noClasses()
                .that().doNotHaveFullyQualifiedName(factoryClassName)
                .and().doNotHaveFullyQualifiedName(invoiceClassName)
                .should().callCodeUnitWhere(DescribedPredicate.describe("a creator of " + invoiceClassName,
                        (com.tngtech.archunit.core.domain.JavaCall<?> call) -> call.getTargetOwner().getName().equals(invoiceClassName)
                                && (call.getTarget().getName().equals("createDraftInvoice") || call.getTarget().getName().equals("<init>"))))
                .because("every V2 invoice draft is created by InvoiceFactory");
    }

    public static ArchRule statusFieldIsWrittenOnlyThroughItsTransitionTable(String aggregateClassName, String fieldName,
                                                                           String transitionMethodName, String machineClassName) {
        return classes()
                .should(writeTheFieldOnlyInTheTransitionMethodThatCallsTheMachine(aggregateClassName, fieldName, transitionMethodName, machineClassName))
                .because("the status " + fieldName + " changes only through its state transition table");
    }

    private static ArchCondition<JavaClass> writeTheFieldOnlyInTheTransitionMethodThatCallsTheMachine(
            String aggregateClassName, String fieldName, String transitionMethodName, String machineClassName) {
        return new ArchCondition<>("write " + fieldName + " only inside " + transitionMethodName + ", which must call " + machineClassName) {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                for (JavaFieldAccess access : origin.getFieldAccessesFromSelf()) {
                    boolean isWrite = access.getAccessType() == JavaFieldAccess.AccessType.SET;
                    boolean isTheField = access.getTargetOwner().getName().equals(aggregateClassName) && access.getTarget().getName().equals(fieldName);
                    boolean insideTransitionMethod = access.getOrigin().getName().equals(transitionMethodName);
                    if (isWrite && isTheField && !insideTransitionMethod) {
                        events.add(SimpleConditionEvent.violated(access, access.getDescription()));
                    }
                }
                if (origin.getName().equals(aggregateClassName)) {
                    boolean callsTheMachine = origin.getMethods().stream()
                            .filter(method -> method.getName().equals(transitionMethodName))
                            .flatMap(method -> method.getMethodCallsFromSelf().stream())
                            .anyMatch(call -> call.getTargetOwner().getName().equals(machineClassName));
                    if (!callsTheMachine) {
                        events.add(SimpleConditionEvent.violated(origin, origin.getName() + "." + transitionMethodName + " does not call " + machineClassName));
                    }
                }
            }
        };
    }

    public static ArchRule billingV2DoesNotDependOnLegacyBilling(String... v2Packages) {
        return noClasses()
                .that().resideInAnyPackage(v2Packages)
                .and().haveNameNotMatching(LEGACY_COUPLED_ISSUING)
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".billing.entity..", ROOT + ".billing.repository..", ROOT + ".billing.service..",
                        ROOT + ".billing.usecase..")
                .allowEmptyShould(true)
                .because("the V2 billing model must not depend on the legacy billing code that V2-02 replaces");
    }

    public static ArchRule paymentAllocationsAreAppendOnly(String allocationClassName, String allocationRepositoryClassName) {
        return classes()
                .should(new ArchCondition<JavaClass>("be append-only") {
                    @Override
                    public void check(JavaClass origin, ConditionEvents events) {
                        if (origin.getName().equals(allocationClassName)) {
                            origin.getMethods().stream()
                                    .filter(method -> method.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC))
                                    .filter(method -> method.getName().startsWith("set"))
                                    .forEach(method -> events.add(SimpleConditionEvent.violated(method, method.getFullName() + " is a public setter of an append-only allocation")));
                        }
                        if (origin.getName().equals(allocationRepositoryClassName)) {
                            origin.getMethods().stream()
                                    .filter(method -> method.getName().startsWith("delete") || method.getName().startsWith("remove")
                                            || method.isAnnotatedWith(org.springframework.data.jpa.repository.Modifying.class))
                                    .forEach(method -> events.add(SimpleConditionEvent.violated(method, method.getFullName() + " modifies allocations")));
                        }
                    }
                })
                .because("payment allocations are never updated nor deleted: a reversal is a new row (D-094)");
    }

    public static ArchRule currentAccountEntriesAreAppendOnly(String entryClassName, String entryRepositoryClassName,
                                                              String accountRepositoryClassName, String allowedModifyingMethod) {
        return classes()
                .should(new ArchCondition<JavaClass>("be append-only") {
                    @Override
                    public void check(JavaClass origin, ConditionEvents events) {
                        if (origin.getName().equals(entryClassName)) {
                            origin.getMethods().stream()
                                    .filter(method -> method.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC))
                                    .filter(method -> !method.getName().startsWith("get"))
                                    .forEach(method -> events.add(SimpleConditionEvent.violated(method, method.getFullName() + " is a public non-getter of an append-only entry")));
                        }
                        if (origin.getName().equals(entryRepositoryClassName)) {
                            origin.getMethods().stream()
                                    .filter(method -> method.getName().startsWith("delete") || method.getName().startsWith("remove")
                                            || method.isAnnotatedWith(org.springframework.data.jpa.repository.Modifying.class))
                                    .forEach(method -> events.add(SimpleConditionEvent.violated(method, method.getFullName() + " modifies entries")));
                        }
                        if (origin.getName().equals(accountRepositoryClassName)) {
                            origin.getMethods().stream()
                                    .filter(method -> method.isAnnotatedWith(org.springframework.data.jpa.repository.Modifying.class))
                                    .filter(method -> !method.getName().equals(allowedModifyingMethod))
                                    .forEach(method -> events.add(SimpleConditionEvent.violated(method, method.getFullName() + " is an unexpected modifying query")));
                        }
                    }
                })
                .because("current account entries are never updated nor deleted (D-144)");
    }

    public static ArchRule methodIsCalledOnlyBy(String ownerClassName, java.util.function.Predicate<String> methodNameFilter,
                                                String allowedCallerClassName, Class<?> allowedCallerType, String description) {
        return methodIsCalledOnlyBy(ownerClassName, methodNameFilter, allowedCallerClassName, allowedCallerType, description, "", Set.of());
    }

    public static ArchRule methodIsCalledOnlyBy(String ownerClassName, java.util.function.Predicate<String> methodNameFilter,
                                                String allowedCallerClassName, Class<?> allowedCallerType, String description,
                                                String bridgeCallerClassName, Set<String> bridgeMethodNames) {
        return noClasses()
                .that().doNotHaveFullyQualifiedName(ownerClassName)
                .and().doNotHaveFullyQualifiedName(allowedCallerClassName)
                .and(DescribedPredicate.not(assignableTo(allowedCallerType)))
                .should().callCodeUnitWhere(DescribedPredicate.describe("a " + description + " on " + ownerClassName,
                        (com.tngtech.archunit.core.domain.JavaCall<?> call) -> call.getTargetOwner().getName().equals(ownerClassName)
                                && methodNameFilter.test(call.getTarget().getName())
                                && !(isTheBridge(call.getOriginOwner().getName(), bridgeCallerClassName)
                                && bridgeMethodNames.contains(call.getTarget().getName()))))
                .allowEmptyShould(true)
                .because(description + " is allowed only to " + allowedCallerType.getSimpleName() + " implementations");
    }

    private static boolean isTheBridge(String callerClassName, String bridgeCallerClassName) {
        if (bridgeCallerClassName.isEmpty()) {
            return false;
        }
        for (String bridge : bridgeCallerClassName.split(",")) {
            if (callerClassName.equals(bridge) || callerClassName.startsWith(bridge + "$")) {
                return true;
            }
        }
        return false;
    }

    public static ArchRule economicStateIsPersistedOnlyByOperations(String invoiceRepositoryClassName, Class<?> operationType) {
        return methodIsCalledOnlyBy(invoiceRepositoryClassName, name -> name.equals("save"), invoiceRepositoryClassName, operationType,
                "save of the invoice");
    }

    public static ArchRule currentAccountIsWrittenOnlyThroughItsService(String serviceClassName, String... repositoryClassNames) {
        return noClasses()
                .that().doNotHaveFullyQualifiedName(serviceClassName)
                .should().callCodeUnitWhere(DescribedPredicate.describe("a save of the current account repositories",
                        (com.tngtech.archunit.core.domain.JavaCall<?> call) -> java.util.Arrays.asList(repositoryClassNames).contains(call.getTargetOwner().getName())
                                && call.getTarget().getName().equals("save")))
                .allowEmptyShould(true)
                .because("the current account is written only through StudentCurrentAccountService");
    }

    public static ArchRule currentAccountServiceIsCalledOnlyByOperations(String serviceClassName, Class<?> operationType) {
        return methodIsCalledOnlyBy(serviceClassName, name -> name.startsWith("record") || name.startsWith("allocate") || name.startsWith("consume") || name.startsWith("restore"), serviceClassName, operationType,
                "recording of an account entry or allocation of its credit", PAYMENT_BRIDGE_UNTIL_PAYMENT_OPERATION, PAYMENT_BRIDGE_ACCOUNT_METHODS);
    }

    public static ArchRule billingClassesLiveInTheirSubdomainFolders(String billingRoot, Set<String> frozenLegacyClasses) {
        return classes()
                .that().resideInAPackage(billingRoot + "..")
                .should(new ArchCondition<JavaClass>("live in billing/<subdomain>/{" + String.join(",", new java.util.TreeSet<>(BILLING_SUBDOMAIN_FOLDERS)) + "} or be a frozen legacy class") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        JavaClass top = javaClass;
                        while (top.getEnclosingClass().isPresent()) {
                            top = top.getEnclosingClass().get();
                        }
                        String pkg = top.getPackageName();
                        String relative = pkg.length() > billingRoot.length() ? pkg.substring(billingRoot.length() + 1) : "";
                        String[] segments = relative.isEmpty() ? new String[0] : relative.split("\\.");
                        boolean allowed;
                        if (segments.length == 0 || top.getSimpleName().equals("package-info")) {
                            allowed = true;
                        } else if (BILLING_LEGACY_FLAT_PACKAGES.contains(segments[0])) {
                            allowed = frozenLegacyClasses.contains(top.getName());
                        } else if (segments.length == 1) {
                            allowed = true;
                        } else {
                            allowed = BILLING_SUBDOMAIN_FOLDERS.contains(segments[1]);
                        }
                        if (!allowed) {
                            events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName() + " is outside the subdomain folders and not a frozen legacy class"));
                        }
                    }
                })
                .because("billing packages put the subdomain first and legacy flat packages cannot grow (D-173)");
    }
}
