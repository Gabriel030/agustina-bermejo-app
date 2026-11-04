package com.red_escuela.backend.shared.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public final class KeyedRateLimiter {
    private static final String OVERFLOW_KEY = "\u0000overflow";

    private final ConcurrentHashMap<String, TrackedBucket> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final int refillPerMinute;
    private final int maxTrackedKeys;
    private final Duration idleWindow;
    private final Clock clock;

    public KeyedRateLimiter(int capacity, int refillPerMinute, int maxTrackedKeys, Duration idleWindow, Clock clock) {
        if (capacity < 1 || refillPerMinute < 1 || maxTrackedKeys < 1) {
            throw new IllegalArgumentException("capacity, refillPerMinute and maxTrackedKeys must be positive");
        }
        this.capacity = capacity;
        this.refillPerMinute = refillPerMinute;
        this.maxTrackedKeys = maxTrackedKeys;
        this.idleWindow = idleWindow;
        this.clock = clock;
    }

    public Decision tryConsume(String key) {
        TrackedBucket tracked = bucketFor(key);
        tracked.touch(clock.instant());
        ConsumptionProbe probe = tracked.bucket().tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return Decision.permitted();
        }
        long seconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).plusMillis(999).toSeconds());
        return Decision.rejected(Duration.ofSeconds(seconds));
    }

    public void evictIdle() {
        Instant threshold = clock.instant().minus(idleWindow);
        buckets.values().removeIf(tracked -> tracked.lastAccessBefore(threshold));
    }

    public int trackedKeys() {
        return buckets.size();
    }

    private TrackedBucket bucketFor(String key) {
        TrackedBucket existing = buckets.get(key);
        if (existing != null) {
            return existing;
        }
        if (buckets.size() >= maxTrackedKeys) {
            evictIdle();
            if (buckets.size() >= maxTrackedKeys) {
                return buckets.computeIfAbsent(OVERFLOW_KEY, ignored -> newTracked());
            }
        }
        return buckets.computeIfAbsent(key, ignored -> newTracked());
    }

    private TrackedBucket newTracked() {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(refillPerMinute, Duration.ofMinutes(1)));
        return new TrackedBucket(Bucket.builder().addLimit(limit).build());
    }

    public record Decision(boolean allowed, Duration retryAfter) {
        static Decision permitted() {
            return new Decision(true, Duration.ZERO);
        }

        static Decision rejected(Duration retryAfter) {
            return new Decision(false, retryAfter);
        }
    }

    private static final class TrackedBucket {
        private final Bucket bucket;
        private volatile Instant lastAccess = Instant.MIN;

        private TrackedBucket(Bucket bucket) {
            this.bucket = bucket;
        }

        private Bucket bucket() {
            return bucket;
        }

        private void touch(Instant now) {
            lastAccess = now;
        }

        private boolean lastAccessBefore(Instant threshold) {
            return lastAccess.isBefore(threshold);
        }
    }
}
