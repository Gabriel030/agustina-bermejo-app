package com.red_escuela.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

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

    private static final List<String> NON_PERF_FILES = List.of(
            "application.properties", "application-prod.properties", "application-staging.properties");

    @Test
    void perfProfileRelaxesRateLimitsAndEnablesFullBilling() throws IOException {
        Properties perf = load("application-perf.properties");

        RELAXED_IN_PERF.forEach((key, value) -> assertThat(perf.getProperty(key)).as(key).isEqualTo(value));
    }

    @Test
    void baseProfileKeepsProductionRateLimitsAndBillingMode() throws IOException {
        Properties base = load("application.properties");

        PRODUCTION_DEFAULTS.forEach((key, value) -> assertThat(base.getProperty(key)).as(key).isEqualTo(value));
    }

    @Test
    void prodAndStagingNeverOverrideRelaxedProperties() throws IOException {
        for (String file : NON_PERF_FILES) {
            if (file.equals("application.properties")) {
                continue;
            }
            Properties properties = load(file);
            RELAXED_IN_PERF.keySet().forEach(key -> assertThat(properties.containsKey(key))
                    .as(file + " must not define " + key)
                    .isFalse());
        }
    }

    @Test
    void productionFilesNeverActivateOrIncludePerfProfile() throws IOException {
        for (String file : NON_PERF_FILES) {
            Properties properties = load(file);
            properties.stringPropertyNames().stream()
                    .filter(key -> key.startsWith("spring.profiles"))
                    .forEach(key -> assertThat(properties.getProperty(key))
                            .as(file + " " + key)
                            .doesNotContain("perf"));
        }
    }

    private static Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = PerfProfileGuardTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in != null) {
                properties.load(in);
            }
        }
        return properties;
    }
}
