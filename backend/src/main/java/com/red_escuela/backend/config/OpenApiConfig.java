package com.red_escuela.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
