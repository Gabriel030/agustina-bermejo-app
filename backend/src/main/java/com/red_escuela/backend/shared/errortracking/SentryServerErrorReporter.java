package com.red_escuela.backend.shared.errortracking;

import io.sentry.Sentry;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class SentryServerErrorReporter implements ServerErrorReporter {

    static final String REQUEST_ID_TAG = "requestId";

    private final SentryEventThrottle eventThrottle;

    public SentryServerErrorReporter(SentryEventThrottle eventThrottle) {
        this.eventThrottle = eventThrottle;
    }

    @Override
    public void reportServerError(Throwable throwable) {
        if (!Sentry.isEnabled()) {
            return;
        }
        if (!eventThrottle.allow(throwable)) {
            return;
        }
        String requestId = MDC.get(REQUEST_ID_TAG);
        Sentry.withScope(scope -> {
            if (requestId != null) {
                scope.setTag(REQUEST_ID_TAG, requestId);
            }
            Sentry.captureException(throwable);
        });
    }
}
