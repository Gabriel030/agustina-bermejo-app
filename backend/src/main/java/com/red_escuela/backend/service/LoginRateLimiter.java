package com.red_escuela.backend.service;

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
 * /auth/login y /auth/forgot-password — el resto de la API ya exige JWT válido como barrera,
 * no necesita esto. Dos buckets independientes: fuerza bruta de login (complementa el lockout
 * por cuenta que ya existe en AuthService) y abuso del envío de tokens de recuperación.
 */
@Component
public class LoginRateLimiter {

    private final ConcurrentHashMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> forgotPasswordBuckets = new ConcurrentHashMap<>();

    private final int loginCapacity;
    private final int loginRefillPerMinute;
    private final int forgotPasswordCapacity;
    private final int forgotPasswordRefillPerMinute;

    public LoginRateLimiter(
            @Value("${auth.rate-limit.login.capacity}") int loginCapacity,
            @Value("${auth.rate-limit.login.refill-per-minute}") int loginRefillPerMinute,
            @Value("${auth.rate-limit.forgot-password.capacity}") int forgotPasswordCapacity,
            @Value("${auth.rate-limit.forgot-password.refill-per-minute}") int forgotPasswordRefillPerMinute) {
        this.loginCapacity = loginCapacity;
        this.loginRefillPerMinute = loginRefillPerMinute;
        this.forgotPasswordCapacity = forgotPasswordCapacity;
        this.forgotPasswordRefillPerMinute = forgotPasswordRefillPerMinute;
    }

    public boolean tryConsumeLogin(String ip) {
        return resolveBucket(loginBuckets, ip, () -> nuevoBucket(loginCapacity, loginRefillPerMinute)).tryConsume(1);
    }

    public boolean tryConsumeForgotPassword(String ip) {
        return resolveBucket(forgotPasswordBuckets, ip, () -> nuevoBucket(forgotPasswordCapacity, forgotPasswordRefillPerMinute))
                .tryConsume(1);
    }

    private Bucket resolveBucket(ConcurrentHashMap<String, Bucket> buckets, String ip, Supplier<Bucket> factory) {
        return buckets.computeIfAbsent(ip, key -> factory.get());
    }

    private Bucket nuevoBucket(int capacity, int refillPerMinute) {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(refillPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}
