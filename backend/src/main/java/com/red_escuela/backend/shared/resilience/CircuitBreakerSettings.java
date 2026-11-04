package com.red_escuela.backend.shared.resilience;

import java.time.Duration;

public record CircuitBreakerSettings(boolean enabled, int window, int failureThreshold, Duration waitOpen) {

    public CircuitBreakerSettings {
        if (window < 1) {
            throw new IllegalArgumentException("window must be at least 1");
        }
        if (failureThreshold < 1 || failureThreshold > window) {
            throw new IllegalArgumentException("failureThreshold must be between 1 and window");
        }
        if (waitOpen == null || waitOpen.isNegative() || waitOpen.isZero()) {
            throw new IllegalArgumentException("waitOpen must be positive");
        }
    }
}
