package com.red_escuela.backend.graduation.certificate.verification.service;

import com.red_escuela.backend.shared.exception.RequestRateLimitExceededException;
import com.red_escuela.backend.shared.ratelimit.KeyedRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
public class PublicVerificationRateLimiter {

    private static final Duration IDLE_WINDOW = Duration.ofMinutes(30);

    private final KeyedRateLimiter limiter;
    private final int missCost;

    public PublicVerificationRateLimiter(
            Clock clock,
            @Value("${certificate.verification.rate-limit.capacity:20}") int capacity,
            @Value("${certificate.verification.rate-limit.refill-per-minute:10}") int refillPerMinute,
            @Value("${certificate.verification.rate-limit.miss-cost:3}") int missCost,
            @Value("${app.rate-limit.max-tracked-keys:50000}") int maxTrackedKeys) {
        this.limiter = new KeyedRateLimiter(capacity, refillPerMinute, maxTrackedKeys, IDLE_WINDOW, clock);
        this.missCost = Math.max(1, missCost);
    }

    public void acquire(String clientKey) {
        KeyedRateLimiter.Decision decision = limiter.tryConsume(keyOf(clientKey));
        if (!decision.allowed()) {
            throw new RequestRateLimitExceededException(decision.retryAfter());
        }
    }

    public void chargeMiss(String clientKey) {
        for (int extra = 1; extra < missCost; extra++) {
            limiter.tryConsume(keyOf(clientKey));
        }
    }

    public static String keyOf(String clientAddress) {
        if (clientAddress == null || clientAddress.indexOf(':') < 0) {
            return clientAddress;
        }
        try {
            byte[] bytes = java.net.InetAddress.getByName(clientAddress).getAddress();
            if (bytes.length != 16) {
                return clientAddress;
            }
            return java.util.HexFormat.of().formatHex(bytes, 0, 8) + "::/64";
        } catch (java.net.UnknownHostException exception) {
            return clientAddress;
        }
    }
}
