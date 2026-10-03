package com.red_escuela.backend.identity.infrastructure;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    private static final Instant START = Instant.parse("2026-10-03T12:00:00Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final Clock clock = new Clock() {
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
            return now.get();
        }
    };

    private LoginRateLimiter newLimiter() {
        return new LoginRateLimiter(5, 5, 3, 3, 5, 5, clock);
    }

    @Test
    void clientsIdleForLongerThanTheEvictionWindowAreForgotten() {
        LoginRateLimiter limiter = newLimiter();
        limiter.tryConsumeLogin("10.0.0.1");
        limiter.tryConsumeForgotPassword("10.0.0.1");
        limiter.tryConsumeRegister("10.0.0.2");

        now.set(START.plus(Duration.ofMinutes(31)));
        limiter.evictIdleClients();

        assertThat(limiter.trackedClients()).isZero();
    }

    @Test
    void recentlyActiveClientsAreKept() {
        LoginRateLimiter limiter = newLimiter();
        limiter.tryConsumeLogin("10.0.0.1");
        now.set(START.plus(Duration.ofMinutes(20)));
        limiter.tryConsumeLogin("10.0.0.2");

        now.set(START.plus(Duration.ofMinutes(35)));
        limiter.evictIdleClients();

        assertThat(limiter.trackedClients()).isEqualTo(1);
    }

    @Test
    void anExhaustedClientIsStillLimitedWhileItKeepsTrying() {
        LoginRateLimiter limiter = newLimiter();
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryConsumeLogin("10.0.0.1")).isTrue();
        }

        limiter.evictIdleClients();

        assertThat(limiter.tryConsumeLogin("10.0.0.1")).isFalse();
    }
}
