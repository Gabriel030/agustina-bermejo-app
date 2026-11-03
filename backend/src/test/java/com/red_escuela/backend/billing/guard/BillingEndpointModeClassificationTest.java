package com.red_escuela.backend.billing.guard;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BillingEndpointModeClassificationTest {

    private static final String WEBHOOK_KEY = "MpWebhookController.webhook POST /payments/mp-webhook";

    private record Endpoint(String controller, String method, String verb, String route, boolean classAnnotatedForBilling,
                            RequiresModule methodRequirement) {

        String key() {
            return controller + "." + method + " " + verb + " " + route;
        }
    }

    @Test
    void everyBillingEndpointIsClassifiedAndEveryClassifiedEndpointExists() throws IOException {
        Map<String, BillingEndpointClassificationFile.Classification> classified = BillingEndpointClassificationFile.read();
        List<String> actual = billingEndpoints().stream().map(Endpoint::key).toList();

        assertThat(actual).hasSizeGreaterThan(100);
        assertThat(actual).doesNotHaveDuplicates();
        assertThat(new TreeSet<>(classified.keySet())).containsExactlyElementsOf(new TreeSet<>(actual));
    }

    @Test
    void everyBillingControllerRequiresBillingAtClassLevel() {
        List<String> withoutClassGuard = billingEndpoints().stream()
                .filter(endpoint -> !endpoint.classAnnotatedForBilling())
                .map(Endpoint::key)
                .toList();

        assertThat(withoutClassGuard).isEmpty();
    }

    @Test
    void everyWriteEndpointDeclaresFullAndNoReadEndpointDoes() throws IOException {
        Map<String, BillingEndpointClassificationFile.Classification> classified = BillingEndpointClassificationFile.read();
        List<String> writesNotRequiringFull = new ArrayList<>();
        List<String> readsRequiringFull = new ArrayList<>();
        for (Endpoint endpoint : billingEndpoints()) {
            BillingEndpointClassificationFile.Classification classification = classified.get(endpoint.key());
            boolean requiresFull = endpoint.methodRequirement() != null
                    && RequiresModule.BILLING.equals(endpoint.methodRequirement().value())
                    && endpoint.methodRequirement().minMode() == BillingMode.FULL;
            if (classification == BillingEndpointClassificationFile.Classification.WRITE && !requiresFull) {
                writesNotRequiringFull.add(endpoint.key());
            }
            if (classification == BillingEndpointClassificationFile.Classification.READ && endpoint.methodRequirement() != null) {
                readsRequiringFull.add(endpoint.key());
            }
        }

        assertThat(writesNotRequiringFull).isEmpty();
        assertThat(readsRequiringFull).isEmpty();
    }

    @Test
    void getEndpointsAreAlwaysReadsAndTheWebhookIsTheOnlyException() throws IOException {
        Map<String, BillingEndpointClassificationFile.Classification> classified = BillingEndpointClassificationFile.read();
        List<String> getsNotRead = classified.entrySet().stream()
                .filter(entry -> entry.getKey().contains(" GET "))
                .filter(entry -> entry.getValue() != BillingEndpointClassificationFile.Classification.READ)
                .map(Map.Entry::getKey)
                .toList();
        List<String> exceptions = classified.entrySet().stream()
                .filter(entry -> entry.getValue() == BillingEndpointClassificationFile.Classification.EXCEPTION_OFF)
                .map(Map.Entry::getKey)
                .toList();

        assertThat(getsNotRead).isEmpty();
        assertThat(exceptions).containsExactly(WEBHOOK_KEY);
    }

    @Test
    void theWebhookStaysAvailableInEveryMode() {
        Endpoint webhook = billingEndpoints().stream().filter(endpoint -> endpoint.key().equals(WEBHOOK_KEY)).findFirst().orElseThrow();

        assertThat(webhook.methodRequirement()).isNull();
        assertThat(webhook.classAnnotatedForBilling()).isTrue();
    }

    private static List<Endpoint> billingEndpoints() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Endpoint> endpoints = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.red_escuela.backend.billing")) {
            Class<?> controller = classOf(definition.getBeanClassName());
            if (controller.getProtectionDomain().getCodeSource().getLocation().getPath().contains("test-classes")) {
                continue;
            }
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String basePath = classMapping == null || classMapping.path().length == 0 ? "" : classMapping.path()[0];
            RequiresModule classRequirement = AnnotatedElementUtils.findMergedAnnotation(controller, RequiresModule.class);
            boolean classGuarded = classRequirement != null && RequiresModule.BILLING.equals(classRequirement.value());
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                RequestMethod verb = mapping.method()[0];
                String route = basePath + (mapping.path().length == 0 ? "" : mapping.path()[0]);
                endpoints.add(new Endpoint(controller.getSimpleName(), method.getName(), verb.name(), route, classGuarded,
                        method.getAnnotation(RequiresModule.class)));
            }
        }
        return endpoints.stream().sorted((first, second) -> first.key().compareTo(second.key())).collect(Collectors.toList());
    }

    private static Class<?> classOf(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
