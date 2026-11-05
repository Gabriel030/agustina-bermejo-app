package com.red_escuela.backend.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.Slice;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

public final class QualityRules {

    static final String ROOT = ArchitectureRules.ROOT;

    static final String[] TRANSACTION_BOUNDARY_PACKAGES = {"..service..", "..operation..", "..repository..", "..api.."};

    static final String[] EXTERNAL_CLIENT_HOME_PACKAGES = {"..adapter..", "..infrastructure..", "..gateway..", ROOT + ".shared.mail.."};

    static final Set<String> BILLING_SUBDOMAINS = Set.of("account", "authorization", "collections", "configuration", "invoice", "note", "operation", "payment");

    static final String[] FEATURES = {"academicrecord", "academicstructure", "announcement", "attendance", "billing",
            "enrollment", "evaluation", "graduation", "identity", "notification", "platform"};

    private static final Set<String> TRANSACTIONAL_ANNOTATIONS = Set.of(
            "org.springframework.transaction.annotation.Transactional", "jakarta.transaction.Transactional");

    private static final Set<String> EVENT_LISTENER_ANNOTATIONS = Set.of(
            "org.springframework.context.event.EventListener",
            "org.springframework.transaction.event.TransactionalEventListener",
            "org.springframework.modulith.events.ApplicationModuleListener");

    private static final Set<String> EXTERNAL_CLIENT_TYPES = Set.of(
            "org.springframework.web.client.RestClient", "org.springframework.web.client.RestTemplate",
            "org.springframework.web.reactive.function.client.WebClient", "org.springframework.mail.javamail.JavaMailSender",
            "org.springframework.mail.MailSender", "java.net.http.HttpClient", "java.net.HttpURLConnection");

    private static final DescribedPredicate<JavaClass> EXTERNAL_CLIENT = DescribedPredicate
            .describe("are an HTTP or mail client", (JavaClass type) -> EXTERNAL_CLIENT_TYPES.contains(type.getName()))
            .or(JavaClass.Predicates.resideInAnyPackage("com.mercadopago..", "jakarta.mail..", "okhttp3..", "software.amazon.awssdk.."));

    private QualityRules() {
    }

    public static ArchRule transactionsAreDeclaredOnlyInSpecialists() {
        return classes()
                .that().resideOutsideOfPackages(TRANSACTION_BOUNDARY_PACKAGES)
                .should(notDeclareTransactional())
                .allowEmptyShould(true)
                .because("a transaction boundary belongs to a specialist (service, operation, repository or api), never to a use case, controller, domain, policy, DTO or entity");
    }

    public static ArchRule eventListenersDoNotDeclareTheirOwnTransaction() {
        return classes()
                .should(notDeclareTransactionalAlongsideAnEventListener())
                .allowEmptyShould(true)
                .because("a listener must not open a second transaction boundary of its own");
    }

    public static ArchRule subpackagesAreFreeOfCycles(String featureRoot) {
        return subpackagesAreFreeOfCycles(featureRoot, Set.of());
    }

    public static ArchRule subpackagesAreFreeOfCycles(String featureRoot, Set<String> onlySubpackages) {
        return slices().matching(featureRoot + ".(*)..")
                .that(DescribedPredicate.describe("are one of " + new java.util.TreeSet<>(onlySubpackages),
                        (Slice slice) -> onlySubpackages.isEmpty() || onlySubpackages.contains(slice.getNamePart(1))))
                .should().beFreeOfCycles()
                .because("the subpackages of " + featureRoot + " must not depend on each other in a cycle");
    }

    public static ArchRule externalClientsAreUsedOnlyFromAdaptersAndInfrastructure() {
        return noClasses()
                .that().resideOutsideOfPackages(EXTERNAL_CLIENT_HOME_PACKAGES)
                .should().dependOnClassesThat(EXTERNAL_CLIENT)
                .allowEmptyShould(true)
                .because("HTTP, mail and provider SDK clients live behind an adapter, infrastructure or shared mail class");
    }

    private static ArchCondition<JavaClass> notDeclareTransactional() {
        return new ArchCondition<>("not declare @Transactional on the class nor on its methods") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (isTransactional(javaClass.getAnnotations())) {
                    events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName() + " declares @Transactional on the class"));
                }
                for (JavaMethod method : javaClass.getMethods()) {
                    if (isTransactional(method.getAnnotations())) {
                        events.add(SimpleConditionEvent.violated(method, method.getFullName() + " declares @Transactional"));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notDeclareTransactionalAlongsideAnEventListener() {
        return new ArchCondition<>("not declare @Transactional on an event listener") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean classTransactional = isTransactional(javaClass.getAnnotations());
                for (JavaMethod method : javaClass.getMethods()) {
                    boolean listener = method.getAnnotations().stream()
                            .anyMatch(annotation -> EVENT_LISTENER_ANNOTATIONS.contains(annotation.getRawType().getName()));
                    if (listener && (classTransactional || isTransactional(method.getAnnotations()))) {
                        events.add(SimpleConditionEvent.violated(method, method.getFullName() + " is an event listener with its own @Transactional"));
                    }
                }
            }
        };
    }

    private static boolean isTransactional(Set<? extends JavaAnnotation<?>> annotations) {
        return annotations.stream().anyMatch(annotation -> TRANSACTIONAL_ANNOTATIONS.contains(annotation.getRawType().getName()));
    }
}
