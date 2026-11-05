package com.red_escuela.backend.testsupport.system;

import com.red_escuela.backend.BackendApplication;
import org.springframework.boot.SpringApplication;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SystemTestApplication {

    private static final String BACKEND_PORT_VARIABLE = "SYSTEM_TEST_BACKEND_PORT";
    private static final String FRONTEND_ORIGIN_VARIABLE = "SYSTEM_TEST_FRONTEND_ORIGIN";
    private static final String HIGH_RATE_LIMIT = "100000";

    private SystemTestApplication() {
    }

    public static void main(String[] args) {
        System.setProperty("spring.devtools.restart.enabled", "false");
        PostgreSQLContainer<?> postgres = startPostgres();
        Runtime.getRuntime().addShutdownHook(new Thread(postgres::stop));
        SpringApplication.from(BackendApplication::main)
                .with(SystemTestSeedConfiguration.class)
                .run(systemTestArguments(postgres, args));
    }

    static PostgreSQLContainer<?> startPostgres() {
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
                .withCommand("postgres", "-c", "fsync=off", "-c", "synchronous_commit=off", "-c", "full_page_writes=off");
        postgres.start();
        return postgres;
    }

    static String[] systemTestArguments(PostgreSQLContainer<?> postgres, String[] passedArguments) {
        List<String> arguments = new ArrayList<>(List.of(
                "--spring.profiles.active=local",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.flyway.locations=classpath:db/migration,classpath:db/testmigration",
                "--jwt.secret=system-test-secret-not-for-production-0123456789",
                "--server.port=" + System.getenv().getOrDefault(BACKEND_PORT_VARIABLE, "8090"),
                "--app.cors.allowed-origins=" + System.getenv().getOrDefault(FRONTEND_ORIGIN_VARIABLE, "http://localhost:4173"),
                "--app.uploads.directory=" + createUploadsDirectory(),
                "--auth.rate-limit.login.capacity=" + HIGH_RATE_LIMIT,
                "--auth.rate-limit.login.refill-per-minute=" + HIGH_RATE_LIMIT,
                "--auth.rate-limit.forgot-password.capacity=" + HIGH_RATE_LIMIT,
                "--auth.rate-limit.forgot-password.refill-per-minute=" + HIGH_RATE_LIMIT,
                "--auth.rate-limit.register.capacity=" + HIGH_RATE_LIMIT,
                "--auth.rate-limit.register.refill-per-minute=" + HIGH_RATE_LIMIT));
        arguments.addAll(List.of(passedArguments));
        return arguments.toArray(String[]::new);
    }

    private static Path createUploadsDirectory() {
        try {
            return Files.createTempDirectory("ab-system-test-uploads-");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
