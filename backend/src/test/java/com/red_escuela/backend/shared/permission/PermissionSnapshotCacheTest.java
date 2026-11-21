package com.red_escuela.backend.shared.permission;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionSnapshotCacheTest {

    private static PermissionSnapshot emptySnapshot() {
        return new PermissionSnapshot(Map.of(), Map.of(), Set.of(), Set.of());
    }

    @Test
    void invalidationDuringALoadPreventsStoringTheStaleSnapshot() {
        AtomicInteger loads = new AtomicInteger();
        PermissionSnapshotCache[] holder = new PermissionSnapshotCache[1];
        PermissionPolicyStore store = () -> {
            if (loads.incrementAndGet() == 1) {
                holder[0].invalidate();
            }
            return emptySnapshot();
        };
        holder[0] = new PermissionSnapshotCache(store, Clock.systemUTC());

        holder[0].current();
        holder[0].current();

        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    void snapshotIsServedFromCacheUntilInvalidated() {
        AtomicInteger loads = new AtomicInteger();
        PermissionSnapshotCache cache = new PermissionSnapshotCache(() -> {
            loads.incrementAndGet();
            return emptySnapshot();
        }, Clock.systemUTC());

        cache.current();
        cache.current();
        cache.invalidate();
        cache.current();

        assertThat(loads.get()).isEqualTo(2);
    }
}
