package com.red_escuela.backend.shared.errortracking;

import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SentryEventThrottle {

    public static final String DROPPED_METER_NAME = "monitoring.events.dropped";

    static final int MAX_TRACKED_FINGERPRINTS = 1000;

    private static final long WINDOW_MILLIS = 60_000L;

    private final int maxPerMinute;
    private final long dedupeMillis;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final Map<String, Long> lastCapturedByFingerprint = new LinkedHashMap<>();
    private final Deque<Long> capturedInWindow = new ArrayDeque<>();

    @Autowired
    public SentryEventThrottle(
            @Value("${sentry.events.max-per-minute:30}") int maxPerMinute,
            @Value("${sentry.events.dedupe-seconds:60}") long dedupeSeconds,
            MeterRegistry meterRegistry) {
        this(maxPerMinute, dedupeSeconds, meterRegistry, Clock.systemUTC());
    }

    SentryEventThrottle(int maxPerMinute, long dedupeSeconds, MeterRegistry meterRegistry, Clock clock) {
        this.maxPerMinute = Math.max(1, maxPerMinute);
        this.dedupeMillis = Math.max(0L, dedupeSeconds) * 1000L;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public boolean allow(Throwable throwable) {
        return allowFingerprint(fingerprintOf(throwable));
    }

    static String fingerprintOf(Throwable throwable) {
        Class<? extends Throwable> type = throwable.getClass();
        return type.getName() + "|" + ErrorCodeResponse.codeOf(type);
    }

    synchronized boolean allowFingerprint(String fingerprint) {
        long now = clock.millis();
        evictExpired(now);
        Long lastCaptured = lastCapturedByFingerprint.get(fingerprint);
        if (lastCaptured != null && now - lastCaptured < dedupeMillis) {
            countDropped("duplicate");
            return false;
        }
        if (capturedInWindow.size() >= maxPerMinute) {
            countDropped("rate_limit");
            return false;
        }
        capturedInWindow.addLast(now);
        lastCapturedByFingerprint.remove(fingerprint);
        lastCapturedByFingerprint.put(fingerprint, now);
        trimToCapacity();
        return true;
    }

    synchronized int trackedFingerprints() {
        return lastCapturedByFingerprint.size();
    }

    private void evictExpired(long now) {
        while (!capturedInWindow.isEmpty() && now - capturedInWindow.peekFirst() >= WINDOW_MILLIS) {
            capturedInWindow.removeFirst();
        }
        Iterator<Long> oldestFirst = lastCapturedByFingerprint.values().iterator();
        while (oldestFirst.hasNext() && now - oldestFirst.next() >= dedupeMillis) {
            oldestFirst.remove();
        }
    }

    private void trimToCapacity() {
        Iterator<String> oldestFirst = lastCapturedByFingerprint.keySet().iterator();
        while (lastCapturedByFingerprint.size() > MAX_TRACKED_FINGERPRINTS && oldestFirst.hasNext()) {
            oldestFirst.next();
            oldestFirst.remove();
        }
    }

    private void countDropped(String reason) {
        meterRegistry.counter(DROPPED_METER_NAME, "reason", reason).increment();
    }
}
