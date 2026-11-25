package com.red_escuela.backend.shared.errortracking;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.sentry.Sentry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SentryServerErrorReporterTest {

    private final AtomicInteger captured = new AtomicInteger();

    private void enableSentryCountingEvents() {
        Sentry.init(options -> {
            options.setDsn("https://publickey@localhost.invalid/1");
            options.setBeforeSend((event, hint) -> {
                captured.incrementAndGet();
                return null;
            });
        });
    }

    @AfterEach
    void closeSentry() {
        Sentry.close();
    }

    @Test
    void repeatedFingerprintIsCapturedOnlyOnce() {
        enableSentryCountingEvents();
        SentryServerErrorReporter reporter = new SentryServerErrorReporter(new SentryEventThrottle(30, 60, new SimpleMeterRegistry()));

        for (int index = 0; index < 10; index++) {
            reporter.reportServerError(new IllegalStateException("boom"));
        }

        assertThat(captured.get()).isEqualTo(1);
    }

    @Test
    void eventsBeyondTheMaximumPerMinuteAreNotCaptured() {
        enableSentryCountingEvents();
        SentryServerErrorReporter reporter = new SentryServerErrorReporter(new SentryEventThrottle(1, 0, new SimpleMeterRegistry()));

        reporter.reportServerError(new IllegalStateException("a"));
        reporter.reportServerError(new IllegalArgumentException("b"));

        assertThat(captured.get()).isEqualTo(1);
    }

    @Test
    void disabledSentryNeverConsultsTheThrottle() {
        SentryEventThrottle throttle = new SentryEventThrottle(30, 60, new SimpleMeterRegistry());
        SentryServerErrorReporter reporter = new SentryServerErrorReporter(throttle);

        reporter.reportServerError(new IllegalStateException("boom"));

        assertThat(throttle.trackedFingerprints()).isZero();
    }
}
