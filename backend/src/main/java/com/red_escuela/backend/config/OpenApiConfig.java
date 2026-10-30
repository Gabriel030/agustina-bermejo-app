package com.red_escuela.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {

    static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI backendOpenApi() {
        return new OpenAPI()
                .info(new Info().title("agustina-bermejo-app backend").version("1"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }

    @Bean
    public OperationCustomizer stableOperationIdCustomizer() {
        return (operation, handlerMethod) -> {
            String controller = handlerMethod.getBeanType().getSimpleName().replaceFirst("Controller$", "");
            operation.setOperationId(controller + "_" + handlerMethod.getMethod().getName());
            return operation;
        };
    }

    private static final Map<String, List<String>> EXTENSIBLE_RESPONSE_ENUMS = Map.of(
            "BillingRunStepResponse", List.of("step"),
            "BillingRunItemResponse", List.of("subjectType"),
            "BillingRunAlertResponse", List.of("type"),
            "MonthlyInvoiceRunItemResponse", List.of("result"),
            "MonthlyInvoiceRunResponse", List.of("mode", "issuanceMode", "status", "trigger"));

    @Bean
    public OpenApiCustomizer extensibleResponseEnumsCustomizer() {
        return openApi -> EXTENSIBLE_RESPONSE_ENUMS.forEach((schemaName, properties) -> {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            properties.forEach(property -> declareEnumExtensible((Schema<?>) schemas.get(schemaName).getProperties().get(property)));
        });
    }

    private static void declareEnumExtensible(Schema<?> property) {
        property.addExtension("x-extensible-enum", property.getEnum());
        property.setEnum(null);
    }
}
