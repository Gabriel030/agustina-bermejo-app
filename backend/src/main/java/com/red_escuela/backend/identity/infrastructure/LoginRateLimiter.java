package com.red_escuela.backend.identity.infrastructure;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import com.red_escuela.backend.shared.ratelimit.KeyedRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class LoginRateLimiter {
    private static final Duration IDLE_EVICTION_WINDOW = Duration.ofMinutes(30);

    private final ConcurrentHashMap<String, TrackedBucket> loginBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TrackedBucket> forgotPasswordBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TrackedBucket> registerBuckets = new ConcurrentHashMap<>();

    private final int loginCapacity;
    private final int loginRefillPerMinute;
    private final int forgotPasswordCapacity;
    private final int forgotPasswordRefillPerMinute;
    private final int registerCapacity;
    private final int registerRefillPerMinute;
    private final KeyedRateLimiter registerUserLimiter;
    private final Clock clock;

    @Autowired
    public LoginRateLimiter(
            @Value("${auth.rate-limit.login.capacity}") int loginCapacity,
            @Value("${auth.rate-limit.login.refill-per-minute}") int loginRefillPerMinute,
            @Value("${auth.rate-limit.forgot-password.capacity}") int forgotPasswordCapacity,
            @Value("${auth.rate-limit.forgot-password.refill-per-minute}") int forgotPasswordRefillPerMinute,
            @Value("${auth.rate-limit.register.capacity}") int registerCapacity,
            @Value("${auth.rate-limit.register.refill-per-minute}") int registerRefillPerMinute,
            @Value("${auth.rate-limit.register-user.capacity}") int registerUserCapacity,
            @Value("${auth.rate-limit.register-user.refill-per-minute}") int registerUserRefillPerMinute,
            @Value("${auth.rate-limit.register-user.max-tracked-keys:10000}") int registerUserMaxTrackedKeys) {
        this(loginCapacity, loginRefillPerMinute, forgotPasswordCapacity, forgotPasswordRefillPerMinute,
                registerCapacity, registerRefillPerMinute, registerUserCapacity, registerUserRefillPerMinute,
                registerUserMaxTrackedKeys, Clock.systemUTC());
    }

    LoginRateLimiter(int loginCapacity, int loginRefillPerMinute, int forgotPasswordCapacity,
                     int forgotPasswordRefillPerMinute, int registerCapacity, int registerRefillPerMinute, int registerUserCapacity,
                     int registerUserRefillPerMinute, int registerUserMaxTrackedKeys, Clock clock) {
        this.loginCapacity = loginCapacity;
        this.loginRefillPerMinute = loginRefillPerMinute;
        this.forgotPasswordCapacity = forgotPasswordCapacity;
        this.forgotPasswordRefillPerMinute = forgotPasswordRefillPerMinute;
        this.registerCapacity = registerCapacity;
        this.registerRefillPerMinute = registerRefillPerMinute;
        this.registerUserLimiter = new KeyedRateLimiter(registerUserCapacity, registerUserRefillPerMinute,
                registerUserMaxTrackedKeys, IDLE_EVICTION_WINDOW, clock);
        this.clock = clock;
    }

    public boolean tryConsumeLogin(String ip) {
        return consume(loginBuckets, ip, () -> newBucket(loginCapacity, loginRefillPerMinute));
    }

    public boolean tryConsumeForgotPassword(String ip) {
        return consume(forgotPasswordBuckets, ip, () -> newBucket(forgotPasswordCapacity, forgotPasswordRefillPerMinute));
    }

    public boolean tryConsumeRegister(String ip) {
        return consume(registerBuckets, ip, () -> newBucket(registerCapacity, registerRefillPerMinute));
    }

    public boolean tryConsumeRegisterForUser(String ip, String username) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String key = ip + "|" + normalizedUsername;
        if (!registerUserLimiter.canTrack(key)) {
            return true;
        }
        return registerUserLimiter.tryConsume(key).allowed();
    }

    @Scheduled(cron = "0 */10 * * * *", zone = "${app.timezone}")
    public void evictIdleClients() {
        registerUserLimiter.evictIdle();
        Instant threshold = clock.instant().minus(IDLE_EVICTION_WINDOW);
        for (ConcurrentHashMap<String, TrackedBucket> buckets : allBuckets()) {
            buckets.values().removeIf(tracked -> tracked.lastAccessBefore(threshold));
        }
    }

    int trackedClients() {
        return allBuckets().stream().mapToInt(ConcurrentHashMap::size).sum() + registerUserLimiter.trackedKeys();
    }

    private List<ConcurrentHashMap<String, TrackedBucket>> allBuckets() {
        return List.of(loginBuckets, forgotPasswordBuckets, registerBuckets);
    }

    private boolean consume(ConcurrentHashMap<String, TrackedBucket> buckets, String ip, Supplier<Bucket> factory) {
        TrackedBucket tracked = buckets.computeIfAbsent(ip, key -> new TrackedBucket(factory.get()));
        tracked.touch(clock.instant());
        return tracked.bucket().tryConsume(1);
    }

    private Bucket newBucket(int capacity, int refillPerMinute) {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(refillPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
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
