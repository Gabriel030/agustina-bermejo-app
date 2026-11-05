package com.red_escuela.backend.config;

import com.red_escuela.backend.shared.time.ApplicationTime;
import com.red_escuela.backend.shared.time.MutableClock;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

@Tag("integration")
@ExtendWith(TestDatabaseIsolation.class)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
            .withCommand("postgres", "-c", "fsync=off", "-c", "synchronous_commit=off", "-c", "full_page_writes=off", "-c", "max_connections=500");

    static final Path uploadsDirectory = createUploadsDirectory();

    static {
        Awaitility.setDefaultPollInterval(Duration.ofMillis(15));
        Awaitility.setDefaultPollDelay(Duration.ZERO);
        postgres.start();
        TestDatabaseIsolation.initialize(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.uploads.directory", uploadsDirectory::toString);
    }

    private static Path createUploadsDirectory() {
        try {
            Path directory = Files.createTempDirectory("ab-test-uploads-");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(directory)));
            return directory;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteRecursively(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
        }
    }

    @AfterEach
    void resetApplicationClock() {
        MutableClock.INSTANCE.reset();
        ApplicationTime.useClock(MutableClock.INSTANCE);
    }
}
