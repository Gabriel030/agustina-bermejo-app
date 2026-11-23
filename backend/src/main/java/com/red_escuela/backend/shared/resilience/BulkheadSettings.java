package com.red_escuela.backend.shared.resilience;

import java.time.Duration;

public record BulkheadSettings(boolean enabled, int maxConcurrent, Duration maxWait) {

    public BulkheadSettings {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("maxConcurrent must be at least 1");
        }
        if (maxWait == null || maxWait.isNegative()) {
            throw new IllegalArgumentException("maxWait must not be negative");
        }
    }
}
