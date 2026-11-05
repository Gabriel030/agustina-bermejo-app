package com.red_escuela.backend.identity.infrastructure;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.gson.GsonFactory;
import com.red_escuela.backend.identity.exception.InvalidGoogleTokenException;
import com.red_escuela.backend.identity.exception.GoogleServiceUnavailableException;
import com.red_escuela.backend.shared.resilience.CircuitBreaker;
import com.red_escuela.backend.shared.resilience.CircuitBreakerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
public class GoogleIdTokenService {
    private final GoogleIdTokenVerifier verifier;
    private final CircuitBreaker breaker;

    @Autowired
    public GoogleIdTokenService(@Value("${google.oauth.client-id}") String clientId,
                                @Value("${google.oauth.http.connect-timeout:5s}") Duration connectTimeout,
                                @Value("${google.oauth.http.read-timeout:5s}") Duration readTimeout,
                                CircuitBreakerFactory circuitBreakerFactory) {
        this(new GoogleIdTokenVerifier.Builder(new TimeoutNetHttpTransport(connectTimeout, readTimeout), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build(), circuitBreakerFactory.breakerNamed(GoogleCircuit.NAME));
    }

    GoogleIdTokenService(GoogleIdTokenVerifier verifier, CircuitBreaker breaker) {
        this.verifier = verifier;
        this.breaker = breaker;
    }

    public GoogleProfile verify(String idTokenString) {
        GoogleIdToken idToken = breaker.execute(() -> {
            try {
                return verifier.verify(idTokenString);
            } catch (IOException e) {
                throw new GoogleServiceUnavailableException(e);
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                throw new InvalidGoogleTokenException();
            }
        }, failure -> failure instanceof GoogleServiceUnavailableException);
        if (idToken == null) {
            throw new InvalidGoogleTokenException();
        }
        GoogleIdToken.Payload payload = idToken.getPayload();
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new InvalidGoogleTokenException();
        }
        return new GoogleProfile(
                payload.getSubject(), payload.getEmail(),
                (String) payload.get("given_name"), (String) payload.get("family_name"));
    }

    public record GoogleProfile(String subject, String email, String givenName, String familyName) {
    }
}
