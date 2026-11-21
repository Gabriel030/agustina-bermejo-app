package com.red_escuela.backend.shared.permission;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;

@Configuration(proxyBeanMethods = false)
class PermissionSecurityConfiguration {

    @Bean
    static AnnotationTemplateExpressionDefaults permissionAnnotationTemplateExpressionDefaults() {
        return new AnnotationTemplateExpressionDefaults();
    }
}
