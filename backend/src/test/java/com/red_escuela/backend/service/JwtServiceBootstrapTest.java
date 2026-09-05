package com.red_escuela.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre la pregunta abierta 1 de la spec ("jwt.secret sin default, falla el boot si falta"):
 * no se prueba invocando un método de JwtService, sino verificando que el contexto de Spring
 * falla al arrancar cuando el placeholder ${JWT_SECRET} no se puede resolver — que es
 * exactamente lo que pasa en application.properties si la env var no está seteada.
 *
 * Requiere PropertyPlaceholderAutoConfiguration explícita: un ApplicationContextRunner
 * "pelado" usa la resolución de placeholders permisiva de Spring core (no tira excepción,
 * deja el placeholder sin resolver), no la estricta que Spring Boot registra vía
 * PropertySourcesPlaceholderConfigurer — que es la que realmente corre en la app.
 */
class JwtServiceBootstrapTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withBean(JwtService.class);

    @Test
    void theContextFailsToStartIfJwtSecretIsNotResolved() {
        contextRunner
                .withPropertyValues(
                        "jwt.secret=${THIS_ENV_VAR_IS_NEVER_SET_ANYWHERE_XYZ123}",
                        "jwt.access-token-expiration-minutes=15")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void theContextStartsOkIfJwtSecretIsSet() {
        contextRunner
                .withPropertyValues(
                        "jwt.secret=un-secret-de-prueba",
                        "jwt.access-token-expiration-minutes=15")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
