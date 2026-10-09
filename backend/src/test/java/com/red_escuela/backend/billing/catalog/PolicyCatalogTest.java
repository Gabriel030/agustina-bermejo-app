package com.red_escuela.backend.billing.catalog;

import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperations;
import com.red_escuela.backend.billing.operation.OperationHandlerRegistry;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.springframework.aop.support.AopUtils;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-051")
class PolicyCatalogTest extends AbstractIntegrationTest {

    private static final Path CATALOG = Path.of("../docs/billing/policy-catalog.md");

    @Autowired
    private ApplicationContext context;

    @Autowired
    private BillingOperations billingOperations;
    @Autowired
    private OperationHandlerRegistry handlerRegistry;

    private List<OperationCatalogEntry> declaredOperations() {
        List<OperationCatalogEntry> entries = new ArrayList<>();
        for (BillingOperation<?, ?> operation : billingOperations.listAll()) {
            List<String> events = operation.publishedEventTypes().stream().map(Class::getSimpleName).toList();
            Map<String, List<String>> listeners = new LinkedHashMap<>();
            for (Class<? extends DomainEvent> eventType : operation.publishedEventTypes()) {
                listeners.put(eventType.getSimpleName(), listenersOf(eventType));
            }
            entries.add(new OperationCatalogEntry(operation.type().name(), operation.validationMoments(),
                    handlerRegistry.listPreHandlersFor(operation.type()).stream().map(handler -> AopUtils.getTargetClass(handler).getSimpleName()).toList(),
                    events, listeners));
        }
        return entries;
    }

    private List<String> listenersOf(Class<? extends DomainEvent> eventType) {
        List<String> listeners = new ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> beanType = context.getType(name);
            if (beanType == null || !beanType.getPackageName().startsWith("com.red_escuela")) {
                continue;
            }
            for (Method method : AopUtils.getTargetClass(context.getBean(name)).getDeclaredMethods()) {
                if (AnnotatedElementUtils.hasAnnotation(method, ApplicationModuleListener.class)
                        && method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(eventType)) {
                    listeners.add(method.getDeclaringClass().getSimpleName() + "#" + method.getName());
                }
            }
        }
        listeners.sort(Comparator.naturalOrder());
        return listeners;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String renderDeclaredCatalog() {
        List<PolicySet<?>> sets = new ArrayList<>();
        for (PolicySet set : context.getBeansOfType(PolicySet.class).values()) {
            sets.add(set);
        }
        return PolicyCatalogRenderer.render(sets, com.red_escuela.backend.shared.domain.statemachine.StateMachineDiscovery.findTablesDeclaredInMain(), declaredOperations());
    }

    @Test
    void committedCatalogMatchesTheDeclaredPolicySets() throws IOException {
        String declared = renderDeclaredCatalog();
        if (Boolean.getBoolean("policy-catalog.write")) {
            Files.writeString(CATALOG, declared);
        }

        assertThat(CATALOG).as("run ./mvnw test -Dtest=PolicyCatalogTest -Dpolicy-catalog.write=true and commit the result").exists();
        assertThat(Files.readString(CATALOG))
                .as("docs/billing/policy-catalog.md is out of date: run ./mvnw test -Dtest=PolicyCatalogTest -Dpolicy-catalog.write=true and commit")
                .isEqualTo(declared);
    }

    @Test
    @Tag("D-178")
    void everyOperationListsItsValidationsHandlersEventsAndPostListeners() {
        String declared = renderDeclaredCatalog();

        assertThat(declared).contains("## Operación CREATE_LATE_PAYMENT_SETTINGS")
                .contains("- Validaciones (momentos): LATE_PAYMENT_SETTINGS")
                .contains("- Pre-procesamiento (en orden): —")
                .contains("- Eventos que publica: LatePaymentSettingsCreatedEvent")
                .contains("- Post-procesamiento de LatePaymentSettingsCreatedEvent: BillingOperationNotificationListener#notifyLatePaymentSettingsCreated");
    }

    @Test
    void everyDeclaredPolicySetAppearsInTheCatalogWithItsPolicies() {
        String declared = renderDeclaredCatalog();

        assertThat(declared).contains("## Momento INVOICE_ISSUING")
                .contains("| 1 | InvoiceIssuingReasonMatchesFeeTypePolicy | FEE_TYPE_NOT_ALLOWED_FOR_REASON | D-050 |")
                .contains("| 6 | ChargeKeyNotAlreadyUsedPolicy | ALREADY_ISSUED | D-009, D-021 |");
    }

    @Test
    void theCatalogIncludesTheStateMachinesSection() {
        assertThat(renderDeclaredCatalog()).contains("## Máquinas de estado")
                .contains("### InvoiceDocumentStatusStateMachine")
                .contains("### InvoicePaymentStatusStateMachine")
                .contains("Transiciones válidas: 7 de 25")
                .contains("Transiciones válidas: 18 de 78");
    }
}
