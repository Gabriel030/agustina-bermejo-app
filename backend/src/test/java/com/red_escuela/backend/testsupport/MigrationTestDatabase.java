package com.red_escuela.backend.testsupport;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class MigrationTestDatabase extends DriverManagerDataSource implements AutoCloseable {

    private final MigrationTemplateCatalog catalog;
    private final String database;

    private MigrationTestDatabase(MigrationTemplateCatalog catalog, String database, DriverManagerDataSource connection) {
        super(connection.getUrl(), connection.getUsername(), connection.getPassword());
        this.catalog = catalog;
        this.database = database;
    }

    public static MigrationTestDatabase migratedTo(String version) {
        return migratedTo(SharedCatalog.INSTANCE, version);
    }

    static MigrationTestDatabase migratedTo(MigrationTemplateCatalog catalog, String version) {
        String database = catalog.createDatabaseAt(version);
        return new MigrationTestDatabase(catalog, database, catalog.dataSourceOf(database));
    }

    static PostgresEndpoint sharedEndpoint() {
        return SharedCatalog.ENDPOINT;
    }

    String databaseName() {
        return database;
    }

    @Override
    public void close() {
        catalog.dropDatabase(database);
    }

    private static final class SharedCatalog {

        static final PostgresEndpoint ENDPOINT = PostgresEndpoint.startFastPostgres();
        static final MigrationTemplateCatalog INSTANCE = new MigrationTemplateCatalog(ENDPOINT, SchemaMigrator.flyway(), "");

        private SharedCatalog() {
        }
    }
}
