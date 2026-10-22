package com.red_escuela.backend.testsupport;

import org.flywaydb.core.api.MigrationVersion;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;
import java.util.TreeMap;

final class MigrationTemplateCatalog {

    private final PostgresEndpoint endpoint;
    private final SchemaMigrator migrator;
    private final String namePrefix;
    private final TreeMap<MigrationVersion, String> templates = new TreeMap<>();
    private int createdDatabases;

    MigrationTemplateCatalog(PostgresEndpoint endpoint, SchemaMigrator migrator, String namePrefix) {
        this.endpoint = endpoint;
        this.migrator = migrator;
        this.namePrefix = namePrefix;
    }

    synchronized String createDatabaseAt(String version) {
        String template = ensureTemplateAt(MigrationVersion.fromVersion(version));
        requireNoOpenConnections(template);
        createdDatabases++;
        String database = namePrefix + "mig_" + createdDatabases + "_v" + sanitized(version);
        administration().execute("create database " + database + " template " + template);
        return database;
    }

    synchronized void dropDatabase(String database) {
        administration().execute("drop database if exists " + database + " with (force)");
    }

    DriverManagerDataSource dataSourceOf(String database) {
        return endpoint.dataSourceOf(database);
    }

    private String ensureTemplateAt(MigrationVersion version) {
        String existing = templates.get(version);
        if (existing != null) {
            return existing;
        }
        String template = namePrefix + "tpl_v" + sanitized(version.getVersion());
        Map.Entry<MigrationVersion, String> previous = templates.lowerEntry(version);
        String source = previous == null ? "" : " template " + previous.getValue();
        administration().execute("create database " + template + source);
        DriverManagerDataSource templateDataSource = endpoint.dataSourceOf(template);
        migrator.migrateTo(templateDataSource, version.getVersion());
        requireMigratedTo(templateDataSource, version, template);
        requireNoOpenConnections(template);
        administration().execute("alter database " + template + " with is_template true allow_connections false");
        templates.put(version, template);
        return template;
    }

    private void requireMigratedTo(DriverManagerDataSource templateDataSource, MigrationVersion version, String template) {
        String lastApplied;
        try {
            lastApplied = new JdbcTemplate(templateDataSource).queryForObject(
                    "select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1", String.class);
        } catch (DataAccessException exception) {
            throw new IllegalStateException("Template " + template + " was not migrated: there is no applied migration", exception);
        }
        if (!version.equals(MigrationVersion.fromVersion(lastApplied))) {
            throw new IllegalStateException("Template " + template + " was migrated to " + lastApplied + " instead of " + version.getVersion());
        }
    }

    private void requireNoOpenConnections(String template) {
        Integer open = administration().queryForObject("select count(*) from pg_stat_activity where datname = ?", Integer.class, template);
        if (open != null && open > 0) {
            throw new IllegalStateException("Template " + template + " still has " + open + " open connections and cannot be cloned");
        }
    }

    private JdbcTemplate administration() {
        return new JdbcTemplate(endpoint.administrationDataSource());
    }

    private static String sanitized(String version) {
        return version.replaceAll("[^0-9A-Za-z]", "_");
    }
}
