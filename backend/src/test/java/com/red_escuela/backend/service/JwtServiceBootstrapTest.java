package com.red_escuela.backend.service;
import com.red_escuela.backend.identity.infrastructure.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceBootstrapTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withBean(JwtService.class);

    @Test
    void contextFailsToStartIfJwtSecretDoesNotResolve() {
        contextRunner
                .withPropertyValues(
                        "jwt.secret=${THIS_ENV_VAR_IS_NEVER_SET_ANYWHERE_XYZ123}",
                        "jwt.access-token-expiration-minutes=15")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void contextStartsOkIfJwtSecretIsSet() {
        contextRunner
                .withPropertyValues(
                        "jwt.secret=un-secret-de-prueba",
                        "jwt.access-token-expiration-minutes=15")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
