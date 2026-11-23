package com.red_escuela.backend.shared.errortracking;

import io.sentry.Sentry;
import io.sentry.SentryOptions;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SentryInitializer implements InitializingBean, DisposableBean {

    private final String dsn;
    private final String environment;
    private final String release;
    private boolean initializedHere;

    public SentryInitializer(
            @Value("${sentry.dsn:}") String dsn,
            @Value("${sentry.environment:local}") String environment,
            @Value("${sentry.release:}") String release) {
        this.dsn = dsn;
        this.environment = environment;
        this.release = release;
    }

    @Override
    public void afterPropertiesSet() {
        if (dsn == null || dsn.isBlank() || Sentry.isEnabled()) {
            return;
        }
        Sentry.init(options -> configure(options, dsn, environment, release));
        initializedHere = true;
    }

    @Override
    public void destroy() {
        if (initializedHere) {
            Sentry.close();
            initializedHere = false;
        }
    }

    static void configure(SentryOptions options, String dsn, String environment, String release) {
        options.setDsn(dsn);
        options.setEnvironment(environment);
        if (release != null && !release.isBlank()) {
            options.setRelease(release);
        }
        options.setSendDefaultPii(false);
        options.setTracesSampleRate(0.0);
        options.setMaxRequestBodySize(SentryOptions.RequestSize.NONE);
        options.setMaxBreadcrumbs(0);
        options.setEnableExternalConfiguration(false);
        options.setEnableUncaughtExceptionHandler(false);
        options.setEnableAutoSessionTracking(false);
        options.setAttachServerName(false);
        options.setBeforeSend(new SentryEventScrubber());
    }
}
