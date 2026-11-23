package com.red_escuela.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class PerfProfileGuardTest {

    private static final Map<String, String> RELAXED_IN_PERF = Map.of(
            "auth.rate-limit.login.capacity", "1000000",
            "auth.rate-limit.login.refill-per-minute", "1000000",
            "app.rate-limit.user-write.capacity", "1000000",
            "app.rate-limit.user-write.refill-per-minute", "1000000",
            "app.rate-limit.user-read.capacity", "1000000",
            "app.rate-limit.user-read.refill-per-minute", "1000000",
            "platform.provisioning.initial-billing-mode", "FULL");

    private static final Map<String, String> PRODUCTION_DEFAULTS = Map.of(
            "auth.rate-limit.login.capacity", "10",
            "auth.rate-limit.login.refill-per-minute", "10",
            "app.rate-limit.user-write.capacity", "${RATE_LIMIT_USER_WRITE_PER_MINUTE:120}",
            "app.rate-limit.user-write.refill-per-minute", "${RATE_LIMIT_USER_WRITE_PER_MINUTE:120}",
            "app.rate-limit.user-read.capacity", "${RATE_LIMIT_USER_READ_PER_MINUTE:600}",
            "app.rate-limit.user-read.refill-per-minute", "${RATE_LIMIT_USER_READ_PER_MINUTE:600}",
            "platform.provisioning.initial-billing-mode", "OFF");


    private static final List<String> OPTIONAL_VARIANT_RESOURCES = List.of(
            "application-prod.yml", "application-prod.yaml", "application.yml", "application.yaml");

    private static final List<Path> PROFILE_TEXT_FILES = List.of(
            Path.of("../docker-compose.prod.yml"),
            Path.of("../deploy/staging/docker-compose.staging.yml"),
            Path.of("Dockerfile"));

    private static final Set<String> RELAXED_NORMALIZED = RELAXED_IN_PERF.keySet().stream()
            .map(PerfProfileGuardTest::normalize)
            .collect(Collectors.toSet());

    @Test
    void perfProfileRelaxesRateLimitsAndEnablesFullBilling() throws IOException {
        Map<String, String> perf = loadRequired("application-perf.properties");

        RELAXED_IN_PERF.forEach((key, value) -> assertThat(perf.get(normalize(key))).as(key).isEqualTo(value));
    }

    @Test
    void baseProfileKeepsProductionRateLimitsAndBillingMode() throws IOException {
        Map<String, String> base = loadRequired("application.properties");

        PRODUCTION_DEFAULTS.forEach((key, value) -> assertThat(base.get(normalize(key))).as(key).isEqualTo(value));
    }

    @Test
    void prodNeverOverridesRelaxedProperties() throws IOException {
        Map<String, String> prod = loadRequired("application-prod.properties");

        assertThat(violations("application-prod.properties", prod)).isEmpty();
        for (String variant : OPTIONAL_VARIANT_RESOURCES) {
            assertThat(violations(variant, loadOptional(variant))).isEmpty();
        }
    }

    @Test
    void nonPerfFilesNeverActivateIncludeOrImportPerfProfile() throws IOException {
        assertThat(perfReferences("application.properties", loadRequired("application.properties"))).isEmpty();
        assertThat(perfReferences("application-prod.properties", loadRequired("application-prod.properties")))
                .isEmpty();
        for (String variant : OPTIONAL_VARIANT_RESOURCES) {
            assertThat(perfReferences(variant, loadOptional(variant))).isEmpty();
        }
    }

    @Test
    void deploymentFilesNeverSelectPerfProfile() throws IOException {
        List<Path> files = new java.util.ArrayList<>(PROFILE_TEXT_FILES);
        Path deployDir = Path.of("../deploy");
        assertThat(deployDir).exists();
        try (Stream<Path> walk = Files.walk(deployDir)) {
            walk.filter(path -> path.getFileName().toString().startsWith(".env")
                            && path.getFileName().toString().contains("example"))
                    .forEach(files::add);
        }
        for (Path file : files) {
            assertThat(file).as(file.toString()).exists();
            List<String> offending = Files.readAllLines(file).stream()
                    .filter(line -> line.toLowerCase(Locale.ROOT).contains("profiles"))
                    .filter(line -> line.toLowerCase(Locale.ROOT).matches(".*\\bperf\\b.*"))
                    .toList();
            assertThat(offending).as(file.toString()).isEmpty();
        }
    }

    private static List<String> violations(String name, Map<String, String> properties) {
        List<String> found = new java.util.ArrayList<>(perfReferences(name, properties));
        properties.keySet().stream()
                .filter(RELAXED_NORMALIZED::contains)
                .forEach(key -> found.add(name + " defines relaxed " + key));
        return found;
    }

    private static List<String> perfReferences(String name, Map<String, String> properties) {
        List<String> found = new java.util.ArrayList<>();
        properties.forEach((key, value) -> {
            if ((key.startsWith("spring.config") || key.startsWith("spring.profiles"))
                    && value.toLowerCase(Locale.ROOT).contains("perf")) {
                found.add(name + " references perf via " + key);
            }
        });
        return found;
    }

    private static String normalize(String key) {
        return key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    private static Map<String, String> loadRequired(String resource) throws IOException {
        Map<String, String> loaded = load(resource);
        assertThat(loaded).as(resource + " must exist and not be empty").isNotNull().isNotEmpty();
        return loaded;
    }

    private static Map<String, String> loadOptional(String resource) throws IOException {
        Map<String, String> loaded = load(resource);
        return loaded == null ? Map.of() : loaded;
    }

    private static Map<String, String> load(String resource) throws IOException {
        try (InputStream in = PerfProfileGuardTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            Map<String, String> result = new java.util.HashMap<>();
            if (resource.endsWith(".properties")) {
                Properties properties = new Properties();
                properties.load(in);
                properties.stringPropertyNames()
                        .forEach(key -> result.put(normalize(key), properties.getProperty(key)));
            } else {
                Iterable<Object> documents = new Yaml().loadAll(in);
                for (Object document : documents) {
                    flatten("", document, result);
                }
            }
            return result;
        }
    }

    private static void flatten(String prefix, Object node, Map<String, String> out) {
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> flatten(prefix.isEmpty() ? key.toString() : prefix + "." + key, value, out));
        } else if (node != null) {
            out.put(normalize(prefix), node.toString());
        }
    }
}
