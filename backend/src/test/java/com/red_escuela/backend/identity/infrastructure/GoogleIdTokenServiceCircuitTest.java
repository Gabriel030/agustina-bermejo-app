package com.red_escuela.backend.identity.infrastructure;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.red_escuela.backend.identity.exception.GoogleServiceUnavailableException;
import com.red_escuela.backend.identity.exception.InvalidGoogleTokenException;
import com.red_escuela.backend.shared.exception.CircuitOpenException;
import com.red_escuela.backend.shared.resilience.CircuitBreaker;
import com.red_escuela.backend.shared.resilience.CircuitBreakerFactory;
import com.red_escuela.backend.shared.resilience.CircuitBreakerState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleIdTokenServiceCircuitTest {

    private GoogleIdTokenVerifier verifier;
    private CircuitBreaker breaker;
    private GoogleIdTokenService service;

    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("resilience.default.enabled", "true")
                .withProperty("resilience.default.window", "2")
                .withProperty("resilience.default.failure-threshold", "2")
                .withProperty("resilience.default.wait-open", "30s");
        breaker = new CircuitBreakerFactory(environment, Clock.systemUTC(), new SimpleMeterRegistry()).breakerNamed(GoogleCircuit.NAME);
        verifier = Mockito.mock(GoogleIdTokenVerifier.class);
        service = new GoogleIdTokenService(verifier, breaker);
    }

    @Test
    void theCircuitOpensAfterRepeatedGoogleOutagesAndStopsCallingTheVerifier() throws Exception {
        Mockito.when(verifier.verify(Mockito.anyString())).thenThrow(new IOException("down"));
        assertThatThrownBy(() -> service.verify("t")).isInstanceOf(GoogleServiceUnavailableException.class);
        assertThatThrownBy(() -> service.verify("t")).isInstanceOf(GoogleServiceUnavailableException.class);
        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);
        assertThatThrownBy(() -> service.verify("t")).isInstanceOf(CircuitOpenException.class);
        Mockito.verify(verifier, Mockito.times(2)).verify(Mockito.anyString());
    }

    @Test
    void anInvalidTokenNeverOpensTheCircuit() throws Exception {
        Mockito.when(verifier.verify(Mockito.anyString())).thenThrow(new GeneralSecurityException("bad"));
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.verify("t")).isInstanceOf(InvalidGoogleTokenException.class);
        }
        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void aTokenRejectedByTheVerifierNeverOpensTheCircuit() throws Exception {
        Mockito.when(verifier.verify(Mockito.anyString())).thenReturn(null);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.verify("t")).isInstanceOf(InvalidGoogleTokenException.class);
        }
        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }
}
