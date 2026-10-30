package com.red_escuela.backend.billing.run.domain;

import com.red_escuela.backend.billing.configuration.domain.IssuanceSettings;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public record FailedInvoiceRetrySchedule(int maxRetryAttempts, List<Integer> backoffHours) {

    public FailedInvoiceRetrySchedule {
        if (maxRetryAttempts < 0) {
            throw new IllegalArgumentException("maxRetryAttempts must not be negative");
        }
        backoffHours = List.copyOf(backoffHours);
        if (backoffHours.size() < maxRetryAttempts) {
            throw new IllegalArgumentException("every retry needs a backoff");
        }
    }

    public static FailedInvoiceRetrySchedule from(IssuanceSettings settings) {
        return new FailedInvoiceRetrySchedule(settings.maxRetryAttempts(), settings.retryBackoffHours());
    }

    public static FailedInvoiceRetrySchedule withoutRetries() {
        return new FailedInvoiceRetrySchedule(0, List.of());
    }

    public Optional<Duration> waitAfterFailedExecution(int failedExecutions) {
        int retriesAlreadyDone = failedExecutions - 1;
        return retriesAlreadyDone < maxRetryAttempts ? Optional.of(Duration.ofHours(backoffHours.get(retriesAlreadyDone))) : Optional.empty();
    }
}
