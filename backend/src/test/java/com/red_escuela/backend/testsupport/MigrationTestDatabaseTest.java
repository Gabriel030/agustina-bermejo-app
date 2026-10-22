package com.red_escuela.backend.testsupport;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class MigrationTestDatabaseTest {

    private static final String LAST_APPLIED_VERSION = "select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1";

    private static MigrationTemplateCatalog catalog(String prefix, SchemaMigrator migrator) {
        return new MigrationTemplateCatalog(MigrationTestDatabase.sharedEndpoint(), migrator, prefix);
    }

    @Test
    void theDatabaseComesWithEveryMigrationUpToTheRequestedVersion() {
        try (MigrationTestDatabase before = MigrationTestDatabase.migratedTo("4");
             MigrationTestDatabase database = MigrationTestDatabase.migratedTo("5")) {
            JdbcTemplate jdbc = new JdbcTemplate(database);

            assertThat(jdbc.queryForObject(LAST_APPLIED_VERSION, String.class)).isEqualTo("5");
            assertThat(jdbc.queryForObject("select to_regclass('role') is not null", Boolean.class)).isTrue();
            assertThat(new JdbcTemplate(before).queryForObject("select to_regclass('role') is null", Boolean.class)).isTrue();
        }
    }

    @Test
    void everyDatabaseIsIndependentFromTheOthersAndFromItsTemplate() {
        try (MigrationTestDatabase first = MigrationTestDatabase.migratedTo("5");
             MigrationTestDatabase second = MigrationTestDatabase.migratedTo("5")) {
            new JdbcTemplate(first).execute("create table only_in_the_first (id int)");

            assertThat(new JdbcTemplate(second).queryForObject("select to_regclass('only_in_the_first') is null", Boolean.class)).isTrue();
            try (MigrationTestDatabase third = MigrationTestDatabase.migratedTo("5")) {
                assertThat(new JdbcTemplate(third).queryForObject("select to_regclass('only_in_the_first') is null", Boolean.class)).isTrue();
            }
        }
    }

    @Test
    void aLaterVersionExtendsTheEarlierTemplateInsteadOfMigratingFromScratch() {
        MigrationTemplateCatalog catalog = catalog("chain_", SchemaMigrator.flyway());
        try (MigrationTestDatabase early = MigrationTestDatabase.migratedTo(catalog, "5");
             MigrationTestDatabase late = MigrationTestDatabase.migratedTo(catalog, "8")) {
            Timestamp installedInEarly = new JdbcTemplate(early).queryForObject("select installed_on from flyway_schema_history where version = '5'", Timestamp.class);
            Timestamp installedInLate = new JdbcTemplate(late).queryForObject("select installed_on from flyway_schema_history where version = '5'", Timestamp.class);

            assertThat(installedInLate).isEqualTo(installedInEarly);
            assertThat(new JdbcTemplate(late).queryForObject(LAST_APPLIED_VERSION, String.class)).isEqualTo("8");
        }
    }

    @Test
    void aTemplateBuiltWithoutApplyingMigrationsIsRejected() {
        MigrationTemplateCatalog catalog = catalog("noop_", (dataSource, version) -> {
        });

        assertThatThrownBy(() -> catalog.createDatabaseAt("5")).isInstanceOf(IllegalStateException.class).hasMessageContaining("was not migrated");
    }

    @Test
    void aTemplateMigratedToTheWrongVersionIsRejected() {
        MigrationTemplateCatalog catalog = catalog("wrong_", (dataSource, version) -> SchemaMigrator.flyway().migrateTo(dataSource, "5"));

        assertThatThrownBy(() -> catalog.createDatabaseAt("6")).isInstanceOf(IllegalStateException.class).hasMessageContaining("instead of 6");
    }

    @Test
    void aTemplateWithAnOpenConnectionIsRejectedBeforeCloning() throws SQLException {
        Connection[] leaked = new Connection[1];
        MigrationTemplateCatalog catalog = catalog("leak_", (dataSource, version) -> {
            SchemaMigrator.flyway().migrateTo(dataSource, version);
            try {
                leaked[0] = dataSource.getConnection();
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        });

        try {
            assertThatThrownBy(() -> catalog.createDatabaseAt("5")).isInstanceOf(IllegalStateException.class).hasMessageContaining("open connections");
        } finally {
            leaked[0].close();
        }
    }

    @Test
    void postgresItselfRefusesToCloneADatabaseThatHasOpenConnections() throws SQLException {
        PostgresEndpoint endpoint = MigrationTestDatabase.sharedEndpoint();
        JdbcTemplate administration = new JdbcTemplate(endpoint.administrationDataSource());
        administration.execute("create database open_source_database");
        DriverManagerDataSource source = endpoint.dataSourceOf("open_source_database");
        try (Connection held = source.getConnection()) {
            assertThat(held.isValid(1)).isTrue();

            assertThatThrownBy(() -> administration.execute("create database clone_of_open_source template open_source_database"))
                    .hasMessageContaining("being accessed by other users");
        } finally {
            administration.execute("drop database if exists clone_of_open_source");
            administration.execute("drop database if exists open_source_database with (force)");
        }
    }
}
