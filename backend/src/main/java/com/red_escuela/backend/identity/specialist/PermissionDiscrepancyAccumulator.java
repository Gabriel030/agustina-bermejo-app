package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.shared.permission.PermissionDiscrepancySink;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PermissionDiscrepancyAccumulator implements PermissionDiscrepancySink {

    private static final Logger log = LoggerFactory.getLogger(PermissionDiscrepancyAccumulator.class);
    private static final Duration FLUSH_INTERVAL = Duration.ofSeconds(30);

    private final PermissionDiscrepancyCounterWriter writer;
    private final Clock clock;
    private final ConcurrentHashMap<PermissionDiscrepancyKey, AtomicLong> pending = new ConcurrentHashMap<>();
    private volatile Instant lastFlushAt;

    public PermissionDiscrepancyAccumulator(PermissionDiscrepancyCounterWriter writer, Clock clock) {
        this.writer = writer;
        this.clock = clock;
        this.lastFlushAt = clock.instant();
    }

    @Override
    public void record(String role, String permission, boolean legacyDecision, boolean permissionDecision) {
        pending.computeIfAbsent(new PermissionDiscrepancyKey(role, permission), key -> new AtomicLong()).incrementAndGet();
        if (!clock.instant().isBefore(lastFlushAt.plus(FLUSH_INTERVAL))) {
            flush();
        }
    }

    @PreDestroy
    public void flush() {
        Instant now = clock.instant();
        lastFlushAt = now;
        Map<PermissionDiscrepancyKey, Long> drained = new HashMap<>();
        for (PermissionDiscrepancyKey key : pending.keySet()) {
            AtomicLong counter = pending.get(key);
            long occurrences = counter == null ? 0 : counter.getAndSet(0);
            if (occurrences > 0) {
                drained.put(key, occurrences);
            }
        }
        if (drained.isEmpty()) {
            return;
        }
        try {
            writer.add(drained, now);
        } catch (RuntimeException failure) {
            log.warn("No se pudieron persistir los contadores de discrepancias de permisos; se reintenta", failure);
            drained.forEach((key, occurrences) ->
                    pending.computeIfAbsent(key, ignored -> new AtomicLong()).addAndGet(occurrences));
        }
    }
}
