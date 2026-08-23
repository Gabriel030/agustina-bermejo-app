package com.red_escuela.backend.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base para tests de integración: un único Postgres efímero para toda la corrida de tests
 * (patrón "singleton container" de Testcontainers), no uno por clase. Con @Testcontainers +
 * @Container (uno por clase) el mismo campo estático se reinicia entre clases y el puerto
 * efímero cambia — algunas clases quedan apuntando al puerto de un contenedor ya parado
 * (probado: Connection refused + timeouts largos de HikariCP). Arrancar una sola vez en un
 * bloque estático evita el problema: nunca se para, lo limpia Ryuk al terminar la JVM.
 */
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
