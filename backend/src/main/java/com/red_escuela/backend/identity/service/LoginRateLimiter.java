package com.red_escuela.backend.identity.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Rate limiting en memoria por IP (Bucket4j, sin infra nueva), defensa en profundidad sobre
 * /auth/login, /auth/forgot-password y /auth/register — el resto de la API ya exige JWT
 * válido como barrera, no necesita esto. Buckets independientes: fuerza bruta de login
 * (complementa el lockout por cuenta que ya existe en AuthService), abuso del envío de
 * tokens de recuperación, y spam de altas de cuenta (registro público, Épica 7).
 */
@Component
public class LoginRateLimiter {

    private final ConcurrentHashMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> forgotPasswordBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> registerBuckets = new ConcurrentHashMap<>();

    private final int loginCapacity;
    private final int loginRefillPerMinute;
    private final int forgotPasswordCapacity;
    private final int forgotPasswordRefillPerMinute;
    private final int registerCapacity;
    private final int registerRefillPerMinute;

    public LoginRateLimiter(
            @Value("${auth.rate-limit.login.capacity}") int loginCapacity,
            @Value("${auth.rate-limit.login.refill-per-minute}") int loginRefillPerMinute,
            @Value("${auth.rate-limit.forgot-password.capacity}") int forgotPasswordCapacity,
            @Value("${auth.rate-limit.forgot-password.refill-per-minute}") int forgotPasswordRefillPerMinute,
            @Value("${auth.rate-limit.register.capacity}") int registerCapacity,
            @Value("${auth.rate-limit.register.refill-per-minute}") int registerRefillPerMinute) {
        this.loginCapacity = loginCapacity;
        this.loginRefillPerMinute = loginRefillPerMinute;
        this.forgotPasswordCapacity = forgotPasswordCapacity;
        this.forgotPasswordRefillPerMinute = forgotPasswordRefillPerMinute;
        this.registerCapacity = registerCapacity;
        this.registerRefillPerMinute = registerRefillPerMinute;
    }

    public boolean tryConsumeLogin(String ip) {
        return resolveBucket(loginBuckets, ip, () -> newBucket(loginCapacity, loginRefillPerMinute)).tryConsume(1);
    }

    public boolean tryConsumeForgotPassword(String ip) {
        return resolveBucket(forgotPasswordBuckets, ip, () -> newBucket(forgotPasswordCapacity, forgotPasswordRefillPerMinute))
                .tryConsume(1);
    }

    public boolean tryConsumeRegister(String ip) {
        return resolveBucket(registerBuckets, ip, () -> newBucket(registerCapacity, registerRefillPerMinute))
                .tryConsume(1);
    }

    private Bucket resolveBucket(ConcurrentHashMap<String, Bucket> buckets, String ip, Supplier<Bucket> factory) {
        return buckets.computeIfAbsent(ip, key -> factory.get());
    }

    private Bucket newBucket(int capacity, int refillPerMinute) {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(refillPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}
