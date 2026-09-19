package com.red_escuela.backend.karate;

import com.intuit.karate.junit5.Karate;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class KarateSuiteTest {
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

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        System.setProperty("karate.server.port", String.valueOf(port));
        KarateSpringContext.set(applicationContext);
    }

    @Karate.Test
    Karate testAuth() {
        return Karate.run("classpath:karate/auth");
    }

    @Karate.Test
    Karate testAutoservicio() {
        return Karate.run("classpath:karate/autoservicio");
    }

    @Karate.Test
    Karate testPersonas() {
        return Karate.run("classpath:karate/personas");
    }

    @Karate.Test
    Karate testProcesosAdministrativos() {
        return Karate.run("classpath:karate/procesos-administrativos");
    }

    @Karate.Test
    Karate testEstructuraAcademica() {
        return Karate.run("classpath:karate/estructura-academica");
    }

    @Karate.Test
    Karate testInscripcionesAcademicas() {
        return Karate.run("classpath:karate/inscripciones-academicas");
    }

    @Karate.Test
    Karate testEvaluacionYAsistencia() {
        return Karate.run("classpath:karate/evaluacion-y-asistencia");
    }

    @Karate.Test
    Karate testCatalogo() {
        return Karate.run("classpath:karate/catalogo");
    }
}
