package com.red_escuela.backend.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

public final class TestDatabaseIsolation implements AfterAllCallback {

    private static final String BASELINE_SCHEMA = "test_baseline";
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(15);

    private static final AtomicLong resets = new AtomicLong();
    private static final AtomicLong totalNanos = new AtomicLong();
    private static DriverManagerDataSource dataSource;
    private static List<String> mutableTables;
    private static List<String> seededTables;

    static synchronized void initialize(String url, String username, String password) {
        dataSource = new DriverManagerDataSource(url, username, password);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println(
                "TestDatabaseIsolation: " + resets.get() + " resets, " + totalNanos.get() / 1_000_000 + " ms total")));
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/testmigration")
                .load()
                .migrate();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            mutableTables = queryTableNames(statement);
            seededTables = new ArrayList<>();
            statement.execute("DROP SCHEMA IF EXISTS " + BASELINE_SCHEMA + " CASCADE");
            statement.execute("CREATE SCHEMA " + BASELINE_SCHEMA);
            for (String table : mutableTables) {
                try (ResultSet rows = statement.executeQuery("SELECT EXISTS (SELECT 1 FROM public.\"" + table + "\")")) {
                    rows.next();
                    if (rows.getBoolean(1)) {
                        seededTables.add(table);
                    }
                }
            }
            for (String table : seededTables) {
                statement.execute("CREATE TABLE " + BASELINE_SCHEMA + ".\"" + table + "\" AS TABLE public.\"" + table + "\"");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not snapshot the seeded test data", exception);
        }
    }

    private static List<String> queryTableNames(Statement statement) throws SQLException {
        List<String> names = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename NOT IN ('flyway_schema_history', 'shedlock') ORDER BY tablename")) {
            while (rows.next()) {
                names.add(rows.getString(1));
            }
        }
        return names;
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (dataSource == null || context.getRequiredTestClass().getEnclosingClass() != null) {
            return;
        }
        long start = System.nanoTime();
        drainAsyncExecutors(context);
        restoreSeededState();
        resets.incrementAndGet();
        totalNanos.addAndGet(System.nanoTime() - start);
    }

    private void drainAsyncExecutors(ExtensionContext context) {
        ApplicationContext applicationContext;
        try {
            applicationContext = SpringExtension.getApplicationContext(context);
        } catch (RuntimeException exception) {
            return;
        }
        long deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos();
        for (ThreadPoolTaskExecutor executor : applicationContext.getBeansOfType(ThreadPoolTaskExecutor.class).values()) {
            ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
            while ((pool.getActiveCount() > 0 || !pool.getQueue().isEmpty()) && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void restoreSeededState() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET session_replication_role = replica");
            StringBuilder tables = new StringBuilder();
            for (String table : mutableTables) {
                if (tables.length() > 0) {
                    tables.append(", ");
                }
                tables.append("public.\"").append(table).append('"');
            }
            statement.execute("TRUNCATE " + tables + " CASCADE");
            for (String table : seededTables) {
                statement.execute("INSERT INTO public.\"" + table + "\" OVERRIDING SYSTEM VALUE SELECT * FROM "
                        + BASELINE_SCHEMA + ".\"" + table + "\"");
            }
            statement.execute("UPDATE shedlock SET lock_until = TIMESTAMP '1970-01-01 00:00:00'");
            statement.execute("SET session_replication_role = DEFAULT");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not restore the seeded test data", exception);
        }
    }
}
