package com.red_escuela.backend.testsupport;

import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

final class PostgresEndpoint {

    private static final String ADMINISTRATION_DATABASE = "postgres";

    private final PostgreSQLContainer<?> container;

    private PostgresEndpoint(PostgreSQLContainer<?> container) {
        this.container = container;
    }

    static PostgresEndpoint startFastPostgres() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
                .withCommand("postgres", "-c", "fsync=off", "-c", "synchronous_commit=off", "-c", "full_page_writes=off", "-c", "max_connections=200");
        container.start();
        return new PostgresEndpoint(container);
    }

    DriverManagerDataSource administrationDataSource() {
        return dataSourceOf(ADMINISTRATION_DATABASE);
    }

    DriverManagerDataSource dataSourceOf(String database) {
        String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + database;
        return new DriverManagerDataSource(url, container.getUsername(), container.getPassword());
    }
}
