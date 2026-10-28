package com.red_escuela.backend.billing.discount.controller;

import com.red_escuela.backend.testsupport.ImportedApplicationClasses;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-432")
class LegacyPromotionContractIsolationTest {
    private static final List<String> LEGACY_ENDPOINTS = List.of("createForCourse", "createForProgram", "close", "findActive");

    @Test
    void nothingButTheLegacyControllerAndItsOwnServicesDependsOnTheLegacyPromotionUseCases() {
        noClasses().that().doNotHaveSimpleName("PromotionController").and().haveSimpleNameNotEndingWith("ByLegacyContractService")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("ByLegacyContractUseCase")
                .because("D-432: el contrato /promotions está deprecado y nada nuevo puede apoyarse en él")
                .check(ImportedApplicationClasses.main());
    }

    @Test
    void everyLegacyPromotionEndpointIsMarkedAsDeprecatedInTheOpenApiContract() {
        List<Method> endpoints = Arrays.stream(PromotionController.class.getDeclaredMethods()).filter(method -> LEGACY_ENDPOINTS.contains(method.getName())).toList();

        assertThat(endpoints).hasSize(LEGACY_ENDPOINTS.size());
        assertThat(endpoints).allSatisfy(method -> assertThat(method.getAnnotation(Operation.class)).isNotNull().extracting(Operation::deprecated).isEqualTo(true));
    }

    @Test
    void theNewContractAndTheLostBenefitsReportAreNotDeprecated() {
        List<Method> others = Arrays.stream(PromotionController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class) || method.isAnnotationPresent(PostMapping.class))
                .filter(method -> !LEGACY_ENDPOINTS.contains(method.getName())).toList();

        assertThat(others).extracting(Method::getName).containsExactly("lostBenefits");
        assertThat(others.get(0).getAnnotation(Operation.class)).isNull();
        assertThat(Arrays.stream(DiscountDefinitionController.class.getDeclaredMethods()).filter(method -> method.isAnnotationPresent(Operation.class))).isEmpty();
    }
}
