package com.red_escuela.backend.config;

import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@Tag("integration")
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

    @AfterEach
    void resetApplicationClock() {
        MutableClock.INSTANCE.reset();
    }
}
