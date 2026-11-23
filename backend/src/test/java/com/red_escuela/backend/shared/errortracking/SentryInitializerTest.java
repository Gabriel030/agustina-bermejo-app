package com.red_escuela.backend.shared.errortracking;

import io.sentry.Sentry;
import io.sentry.SentryOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SentryInitializerTest {

    private static final String DUMMY_DSN = "https://publickey@localhost.invalid/1";

    @AfterEach
    void closeSentry() {
        Sentry.close();
    }

    @Test
    void configureAppliesEverySafeOption() {
        SentryOptions options = new SentryOptions();

        SentryInitializer.configure(options, DUMMY_DSN, "prod", "1.2.3");

        assertThat(options.getEnvironment()).isEqualTo("prod");
        assertThat(options.getRelease()).isEqualTo("1.2.3");
        assertThat(options.isSendDefaultPii()).isFalse();
        assertThat(options.getTracesSampleRate()).isEqualTo(0.0);
        assertThat(options.getMaxRequestBodySize()).isEqualTo(SentryOptions.RequestSize.NONE);
        assertThat(options.getMaxBreadcrumbs()).isZero();
        assertThat(options.isEnableExternalConfiguration()).isFalse();
        assertThat(options.isEnableUncaughtExceptionHandler()).isFalse();
        assertThat(options.isEnableAutoSessionTracking()).isFalse();
        assertThat(options.isAttachServerName()).isFalse();
        assertThat(options.isAttachThreads()).isFalse();
        assertThat(options.isSendClientReports()).isFalse();
        assertThat(options.getBeforeSend()).isInstanceOf(SentryEventScrubber.class);
    }

    @Test
    void aBlankReleaseIsNotSet() {
        SentryOptions options = new SentryOptions();

        SentryInitializer.configure(options, DUMMY_DSN, "prod", " ");

        assertThat(options.getRelease()).isNull();
    }

    @Test
    void initializerEnablesSentryWithADsnAndClosesCleanly() {
        SentryInitializer initializer = new SentryInitializer(DUMMY_DSN, "test", "1.0");

        initializer.afterPropertiesSet();
        assertThat(Sentry.isEnabled()).isTrue();

        initializer.destroy();
        assertThat(Sentry.isEnabled()).isFalse();
    }

    @Test
    void initializerWithoutDsnLeavesSentryDisabled() {
        SentryInitializer initializer = new SentryInitializer("", "test", "1.0");

        initializer.afterPropertiesSet();
        initializer.destroy();

        assertThat(Sentry.isEnabled()).isFalse();
    }
}
