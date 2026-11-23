package com.red_escuela.backend.shared.resilience;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BulkheadFactory {
    private static final String DEFAULT_SCOPE = "default";

    private final Environment environment;
    private final MeterRegistry meterRegistry;
    private final Map<String, Bulkhead> bulkheads = new ConcurrentHashMap<>();

    public BulkheadFactory(Environment environment, MeterRegistry meterRegistry) {
        this.environment = environment;
        this.meterRegistry = meterRegistry;
    }

    public Bulkhead bulkheadNamed(String name) {
        return bulkheads.computeIfAbsent(name, this::create);
    }

    private Bulkhead create(String name) {
        BulkheadSettings settings = new BulkheadSettings(
                property(name, "enabled", Boolean.class),
                property(name, "max-concurrent", Integer.class),
                property(name, "max-wait", Duration.class));
        return new Bulkhead(name, settings, meterRegistry);
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
