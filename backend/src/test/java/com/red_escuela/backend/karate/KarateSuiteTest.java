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

/**
 * QA de contrato HTTP con Karate (ver docs/context/testing.md, "QA de contrato HTTP
 * (Karate)") — a diferencia de los tests de MockMvc de arriba, estos .feature corren
 * contra un servidor HTTP real (RANDOM_PORT). Encontró 2 bugs reales que MockMvc no
 * atrapaba (ver historial de RegistrationFlowTest / GlobalExceptionHandler) antes de
 * migrarse acá desde una colección de Bruno standalone.
 *
 * A propósito NO extiende AbstractIntegrationTest (el Postgres singleton compartido por
 * el resto de la suite): esta clase no es @Transactional (si lo fuera, todos los .feature
 * correrían dentro de una única transacción de test y enmascararían bugs reales de
 * boundary transaccional — el mismo problema que encontramos con el rol ALUMNO no
 * otorgado). Sin rollback, cada Student/Person/LoginAttempt que un .feature crea queda
 * en la base — y varios tests de otras clases (AuthServiceTest, StudentControllerTest,
 * ProspectControllerTest) asumen "cuento todas las filas de la tabla y espero exactamente
 * N", asunción que era cierta porque hasta ahora TODA la suite hacía rollback. Compartir
 * el Postgres singleton con Karate rompía esa asunción (confirmado: 4 tests empezaron a
 * fallar apenas Karate corrió en la misma base). Fix: Postgres propio para Karate, nunca
 * compartido — nadie más necesita limpiar nada, ni Karate necesita @Transactional.
 */
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
}
