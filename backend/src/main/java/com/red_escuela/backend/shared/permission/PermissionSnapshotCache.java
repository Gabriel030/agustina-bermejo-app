package com.red_escuela.backend.shared.permission;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PermissionSnapshotCache {

    private static final Duration TTL = Duration.ofSeconds(30);

    private final PermissionPolicyStore store;
    private final Clock clock;
    private final AtomicLong generation = new AtomicLong();
    private volatile Entry entry;

    public PermissionSnapshotCache(PermissionPolicyStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public PermissionSnapshot current() {
        Instant now = clock.instant();
        Entry held = entry;
        if (held != null && now.isBefore(held.expiresAt())) {
            return held.snapshot();
        }
        synchronized (this) {
            held = entry;
            if (held != null && now.isBefore(held.expiresAt())) {
                return held.snapshot();
            }
            long loadedAt = generation.get();
            Entry fresh = new Entry(store.load(), now.plus(TTL));
            if (generation.get() == loadedAt) {
                entry = fresh;
            }
            return fresh.snapshot();
        }
    }

    public void invalidate() {
        generation.incrementAndGet();
        entry = null;
    }

    private record Entry(PermissionSnapshot snapshot, Instant expiresAt) {
    }
}
