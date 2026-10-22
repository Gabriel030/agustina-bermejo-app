package com.red_escuela.backend.testsupport;

import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

@FunctionalInterface
interface SchemaMigrator {

    void migrateTo(DataSource dataSource, String version);

    static SchemaMigrator flyway() {
        return (dataSource, version) -> Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }
}
