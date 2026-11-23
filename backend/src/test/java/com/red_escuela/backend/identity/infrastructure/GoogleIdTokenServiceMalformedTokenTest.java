package com.red_escuela.backend.identity.infrastructure;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.gson.GsonFactory;
import com.red_escuela.backend.identity.exception.InvalidGoogleTokenException;
import com.red_escuela.backend.shared.resilience.Bulkhead;
import com.red_escuela.backend.shared.resilience.BulkheadSettings;
import com.red_escuela.backend.shared.resilience.CircuitBreaker;
import com.red_escuela.backend.shared.resilience.CircuitBreakerFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleIdTokenServiceMalformedTokenTest {

    private final AtomicInteger networkCalls = new AtomicInteger();
    private GoogleIdTokenService service;

    @BeforeEach
    void setUp() {
        HttpTransport offlineTransport = new HttpTransport() {
            @Override
            protected LowLevelHttpRequest buildRequest(String method, String url) {
                networkCalls.incrementAndGet();
                throw new IllegalStateException("no network allowed");
            }
        };
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(offlineTransport, GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList("expected-client-id"))
                .build();
        MockEnvironment environment = new MockEnvironment().withProperty("resilience.default.enabled", "true")
                .withProperty("resilience.default.window", "10")
                .withProperty("resilience.default.failure-threshold", "5")
                .withProperty("resilience.default.wait-open", "30s");
        CircuitBreaker breaker = new CircuitBreakerFactory(environment, Clock.systemUTC(), new SimpleMeterRegistry())
                .breakerNamed(GoogleCircuit.NAME);
        Bulkhead bulkhead = new Bulkhead(GoogleCircuit.NAME, new BulkheadSettings(true, 4, Duration.ofMillis(50)),
                new SimpleMeterRegistry());
        service = new GoogleIdTokenService(verifier, breaker, bulkhead);
    }

    private static String segment(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    }

    @Test
    void structurallyInvalidTokensAreRejectedAsInvalidWithoutCallingGoogle() {
        List<String> tokens = List.of("", "garbage", "a.b.c", "a.b", "....", "' OR 1=1--", "A".repeat(10_000),
                segment("{}") + "." + segment("{}") + "." + segment("sig"),
                segment("{\"alg\":\"none\"}") + "." + segment("{\"sub\":\"1\",\"email\":\"x@example.com\"}") + ".");
        for (String token : tokens) {
            assertThatThrownBy(() -> service.verify(token))
                    .as(token.substring(0, Math.min(15, token.length())))
                    .isInstanceOf(InvalidGoogleTokenException.class);
        }
        assertThat(networkCalls).hasValue(0);
    }

    @Test
    void anUnsignedTokenWithoutIssuedAtCurrentlyEscapesAsNullPointerInsteadOfInvalidToken() {
        String unsigned = segment("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "."
                + segment("{\"iss\":\"https://accounts.google.com\",\"aud\":\"expected-client-id\",\"sub\":\"1\","
                + "\"email\":\"victim@example.com\",\"email_verified\":true,\"exp\":4102444800}") + ".";

        assertThatThrownBy(() -> service.verify(unsigned))
                .isInstanceOf(NullPointerException.class);
        assertThat(networkCalls).hasValue(0);
    }
}
