package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.shared.exception.RequestRateLimitExceededException;
import com.red_escuela.backend.shared.ratelimit.KeyedRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CalendarFeedRateLimiter {

    private static final Duration IDLE_WINDOW = Duration.ofMinutes(30);

    private final KeyedRateLimiter tokenLimiter;
    private final KeyedRateLimiter missLimiter;
    private final ConcurrentHashMap<String, Instant> blockedUntilByClient = new ConcurrentHashMap<>();
    private final int missCost;
    private final int maxTrackedKeys;
    private final Clock clock;

    public CalendarFeedRateLimiter(
            Clock clock,
            @Value("${calendar.feed.rate-limit.token-capacity:120}") int tokenCapacity,
            @Value("${calendar.feed.rate-limit.token-refill-per-minute:2}") int tokenRefillPerMinute,
            @Value("${calendar.feed.rate-limit.miss-capacity:60}") int missCapacity,
            @Value("${calendar.feed.rate-limit.miss-refill-per-minute:30}") int missRefillPerMinute,
            @Value("${calendar.feed.rate-limit.miss-cost:3}") int missCost,
            @Value("${app.rate-limit.max-tracked-keys:50000}") int maxTrackedKeys) {
        this.clock = clock;
        this.maxTrackedKeys = maxTrackedKeys;
        this.tokenLimiter = new KeyedRateLimiter(tokenCapacity, tokenRefillPerMinute, maxTrackedKeys, IDLE_WINDOW, clock);
        this.missLimiter = new KeyedRateLimiter(missCapacity, missRefillPerMinute, maxTrackedKeys, IDLE_WINDOW, clock);
        this.missCost = Math.max(1, missCost);
    }

    public void assertClientIsNotBlocked(String clientAddress) {
        String key = keyOf(clientAddress);
        Instant blockedUntil = blockedUntilByClient.get(key);
        if (blockedUntil == null) {
            return;
        }
        Instant now = clock.instant();
        if (!blockedUntil.isAfter(now)) {
            blockedUntilByClient.remove(key, blockedUntil);
            return;
        }
        throw new RequestRateLimitExceededException(secondsUntil(now, blockedUntil));
    }

    public void chargeMiss(String clientAddress) {
        String key = keyOf(clientAddress);
        for (int attempt = 0; attempt < missCost; attempt++) {
            KeyedRateLimiter.Decision decision = missLimiter.tryConsume(key);
            if (!decision.allowed()) {
                blockClient(key, clock.instant().plus(decision.retryAfter()));
                return;
            }
        }
    }

    public void acquireForToken(String tokenHash) {
        KeyedRateLimiter.Decision decision = tokenLimiter.tryConsume(tokenHash);
        if (!decision.allowed()) {
            throw new RequestRateLimitExceededException(decision.retryAfter());
        }
    }

    @Scheduled(cron = "0 */10 * * * *", zone = "${app.timezone}")
    public void evictIdleEntries() {
        tokenLimiter.evictIdle();
        missLimiter.evictIdle();
        Instant now = clock.instant();
        blockedUntilByClient.values().removeIf(blockedUntil -> !blockedUntil.isAfter(now));
    }

    private void blockClient(String key, Instant blockedUntil) {
        if (blockedUntilByClient.size() >= maxTrackedKeys) {
            Instant now = clock.instant();
            blockedUntilByClient.values().removeIf(existing -> !existing.isAfter(now));
            if (blockedUntilByClient.size() >= maxTrackedKeys) {
                return;
            }
        }
        blockedUntilByClient.merge(key, blockedUntil, (current, candidate) -> candidate.isAfter(current) ? candidate : current);
    }

    private static Duration secondsUntil(Instant now, Instant target) {
        long seconds = Math.max(1, Duration.between(now, target).plusMillis(999).toSeconds());
        return Duration.ofSeconds(seconds);
    }

    static String keyOf(String clientAddress) {
        if (clientAddress == null || clientAddress.indexOf(':') < 0) {
            return clientAddress == null ? "unknown" : clientAddress;
        }
        try {
            byte[] bytes = InetAddress.getByName(clientAddress).getAddress();
            if (bytes.length != 16) {
                return clientAddress;
            }
            return HexFormat.of().formatHex(bytes, 0, 8) + "::/64";
        } catch (UnknownHostException exception) {
            return clientAddress;
        }
    }
}
