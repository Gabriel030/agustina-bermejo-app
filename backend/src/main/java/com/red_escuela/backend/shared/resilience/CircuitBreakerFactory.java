package com.red_escuela.backend.shared.resilience;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CircuitBreakerFactory {
    private static final String DEFAULT_SCOPE = "default";

    private final Environment environment;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    public CircuitBreakerFactory(Environment environment, Clock clock, MeterRegistry meterRegistry) {
        this.environment = environment;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    public CircuitBreaker breakerNamed(String name) {
        return breakers.computeIfAbsent(name, this::create);
    }

    private CircuitBreaker create(String name) {
        CircuitBreakerSettings settings = new CircuitBreakerSettings(
                property(name, "enabled", Boolean.class),
                property(name, "window", Integer.class),
                property(name, "failure-threshold", Integer.class),
                property(name, "wait-open", Duration.class));
        return new CircuitBreaker(name, settings, clock, meterRegistry);
    }

    private <T> T bind(String key, Class<T> type) {
        return Binder.get(environment).bind(key, Bindable.of(type)).orElse(null);
    }

    private <T> T property(String name, String key, Class<T> type) {
        T specific = bind("resilience." + name + "." + key, type);
        if (specific != null) {
            return specific;
        }
        T fallback = bind("resilience." + DEFAULT_SCOPE + "." + key, type);
        if (fallback == null) {
            throw new IllegalStateException("Missing property resilience." + DEFAULT_SCOPE + "." + key);
        }
        return fallback;
    }
}
