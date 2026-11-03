package com.red_escuela.backend.shared.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyedRateLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-03T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
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

    private final MutableClock clock = new MutableClock();

    @Test
    void theRequestAfterTheCapacityIsRejectedWithAPositiveRetryAfter() {
        KeyedRateLimiter limiter = new KeyedRateLimiter(3, 3, 100, Duration.ofMinutes(30), clock);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryConsume("1.1.1.1").allowed()).isTrue();
        }
        KeyedRateLimiter.Decision rejected = limiter.tryConsume("1.1.1.1");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfter()).isBetween(Duration.ofSeconds(1), Duration.ofSeconds(60));
    }

    @Test
    void eachKeyHasItsOwnBucket() {
        KeyedRateLimiter limiter = new KeyedRateLimiter(1, 1, 100, Duration.ofMinutes(30), clock);

        assertThat(limiter.tryConsume("a").allowed()).isTrue();
        assertThat(limiter.tryConsume("a").allowed()).isFalse();
        assertThat(limiter.tryConsume("b").allowed()).isTrue();
    }

    @Test
    void idleKeysAreEvictedSoTheMapDoesNotGrowForever() {
        KeyedRateLimiter limiter = new KeyedRateLimiter(1, 1, 100, Duration.ofMinutes(30), clock);
        limiter.tryConsume("old");
        clock.advance(Duration.ofMinutes(31));
        limiter.tryConsume("recent");

        limiter.evictIdle();

        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    @Test
    void whenTheMapIsFullOfActiveKeysNewKeysShareOneOverflowBucketInsteadOfGrowing() {
        KeyedRateLimiter limiter = new KeyedRateLimiter(1, 1, 2, Duration.ofMinutes(30), clock);
        limiter.tryConsume("a");
        limiter.tryConsume("b");

        assertThat(limiter.tryConsume("c").allowed()).isTrue();
        assertThat(limiter.tryConsume("d").allowed()).isFalse();
        assertThat(limiter.trackedKeys()).isEqualTo(3);
        limiter.tryConsume("e");
        limiter.tryConsume("f");
        assertThat(limiter.trackedKeys()).isEqualTo(3);
    }

    @Test
    void whenTheMapIsFullButSomeKeysAreIdleTheyAreEvictedToMakeRoom() {
        KeyedRateLimiter limiter = new KeyedRateLimiter(1, 1, 2, Duration.ofMinutes(30), clock);
        limiter.tryConsume("a");
        limiter.tryConsume("b");
        clock.advance(Duration.ofMinutes(31));

        assertThat(limiter.tryConsume("c").allowed()).isTrue();
        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    @Test
    void nonPositiveLimitsAreRejected() {
        assertThatThrownBy(() -> new KeyedRateLimiter(0, 1, 1, Duration.ofMinutes(1), clock))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
