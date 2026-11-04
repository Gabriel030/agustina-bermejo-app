package com.red_escuela.backend.identity.infrastructure;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.gson.GsonFactory;
import com.red_escuela.backend.identity.exception.InvalidGoogleTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
public class GoogleIdTokenService {
    private final GoogleIdTokenVerifier verifier;

    public GoogleIdTokenService(@Value("${google.oauth.client-id}") String clientId,
                                @Value("${google.oauth.http.connect-timeout:5s}") Duration connectTimeout,
                                @Value("${google.oauth.http.read-timeout:5s}") Duration readTimeout) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new TimeoutNetHttpTransport(connectTimeout, readTimeout), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public GoogleProfile verify(String idTokenString) {
        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw new InvalidGoogleTokenException();
        }
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
