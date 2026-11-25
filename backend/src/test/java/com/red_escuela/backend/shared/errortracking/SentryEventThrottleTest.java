package com.red_escuela.backend.shared.errortracking;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SentryEventThrottleTest {

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-25T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MutableClock clock;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        meterRegistry = new SimpleMeterRegistry();
    }

    private SentryEventThrottle throttle(int maxPerMinute, long dedupeSeconds) {
        return new SentryEventThrottle(maxPerMinute, dedupeSeconds, meterRegistry, clock);
    }

    private double dropped(String reason) {
        return meterRegistry.counter(SentryEventThrottle.DROPPED_METER_NAME, "reason", reason).count();
    }

    @Test
    void sameFingerprintInsideTheWindowIsCapturedOnlyOnce() {
        SentryEventThrottle throttle = throttle(30, 60);

        assertThat(throttle.allow(new IllegalStateException("a"))).isTrue();
        clock.advance(Duration.ofSeconds(30));
        assertThat(throttle.allow(new IllegalStateException("b"))).isFalse();
        assertThat(throttle.allow(new IllegalStateException("c"))).isFalse();

        assertThat(dropped("duplicate")).isEqualTo(2.0);
    }

    @Test
    void sameFingerprintIsCapturedAgainOnceTheWindowPasses() {
        SentryEventThrottle throttle = throttle(30, 60);

        assertThat(throttle.allow(new IllegalStateException("a"))).isTrue();
        clock.advance(Duration.ofSeconds(59));
        assertThat(throttle.allow(new IllegalStateException("a"))).isFalse();
        clock.advance(Duration.ofSeconds(1));
        assertThat(throttle.allow(new IllegalStateException("a"))).isTrue();
    }

    @Test
    void differentExceptionClassesHaveDifferentFingerprints() {
        SentryEventThrottle throttle = throttle(30, 60);

        assertThat(throttle.allow(new IllegalStateException("a"))).isTrue();
        assertThat(throttle.allow(new IllegalArgumentException("a"))).isTrue();
    }

    @Test
    void capturesBeyondTheMaximumPerMinuteAreDroppedAndResumeAfterTheMinute() {
        SentryEventThrottle throttle = throttle(3, 0);

        assertThat(throttle.allowFingerprint("a")).isTrue();
        assertThat(throttle.allowFingerprint("b")).isTrue();
        assertThat(throttle.allowFingerprint("c")).isTrue();
        assertThat(throttle.allowFingerprint("d")).isFalse();
        assertThat(throttle.allowFingerprint("e")).isFalse();
        assertThat(dropped("rate_limit")).isEqualTo(2.0);

        clock.advance(Duration.ofSeconds(59));
        assertThat(throttle.allowFingerprint("f")).isFalse();
        clock.advance(Duration.ofSeconds(1));
        assertThat(throttle.allowFingerprint("f")).isTrue();
    }

    @Test
    void fingerprintMapStaysBoundedWhenMoreFingerprintsThanTheMaximumArrive() {
        SentryEventThrottle throttle = throttle(100_000, 3600);

        for (int index = 0; index < SentryEventThrottle.MAX_TRACKED_FINGERPRINTS * 2; index++) {
            throttle.allowFingerprint("fingerprint-" + index);
        }

        assertThat(throttle.trackedFingerprints()).isEqualTo(SentryEventThrottle.MAX_TRACKED_FINGERPRINTS);
    }

    @Test
    void expiredFingerprintsAreSweptFromTheMap() {
        SentryEventThrottle throttle = throttle(100, 60);

        throttle.allowFingerprint("a");
        throttle.allowFingerprint("b");
        clock.advance(Duration.ofSeconds(61));
        throttle.allowFingerprint("c");

        assertThat(throttle.trackedFingerprints()).isEqualTo(1);
    }

    @Test
    void fingerprintCombinesExceptionClassAndErrorCode() {
        assertThat(SentryEventThrottle.fingerprintOf(new IllegalStateException("x")))
                .startsWith(IllegalStateException.class.getName() + "|");
    }
}
