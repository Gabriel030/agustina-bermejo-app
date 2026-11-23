package com.red_escuela.backend.shared.errortracking;

import io.sentry.Sentry;
import io.sentry.SentryOptions;
import java.util.function.Consumer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SentryInitializer implements InitializingBean, DisposableBean {

    private final String dsn;
    private final String environment;
    private final String release;
    private final Consumer<SentryOptions> extraOptions;
    private boolean initializedHere;

    @Autowired
    public SentryInitializer(
            @Value("${sentry.dsn:}") String dsn,
            @Value("${sentry.environment:local}") String environment,
            @Value("${sentry.release:}") String release) {
        this(dsn, environment, release, options -> { });
    }

    SentryInitializer(String dsn, String environment, String release, Consumer<SentryOptions> extraOptions) {
        this.dsn = dsn;
        this.environment = environment;
        this.release = release;
        this.extraOptions = extraOptions;
    }

    @Override
    public void afterPropertiesSet() {
        if (dsn == null || dsn.isBlank() || Sentry.isEnabled()) {
            return;
        }
        Sentry.init(options -> {
            configure(options, dsn, environment, release);
            extraOptions.accept(options);
        });
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
        options.setAttachThreads(false);
        options.setSendClientReports(false);
        options.setBeforeSend(new SentryEventScrubber());
    }
}
