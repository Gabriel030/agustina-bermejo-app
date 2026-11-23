package com.red_escuela.backend.shared.errortracking;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import io.sentry.Sentry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SentryInertTest extends AbstractIntegrationTest {

    @Autowired
    private ServerErrorReporter serverErrorReporter;

    @Test
    void sentryStaysDisabledWhenNoDsnIsConfigured() {
        assertThat(Sentry.isEnabled()).isFalse();
    }

    @Test
    void reportingAnErrorWithSentryDisabledDoesNothing() {
        serverErrorReporter.reportServerError(new RuntimeException("boom"));

        assertThat(Sentry.isEnabled()).isFalse();
    }
}
