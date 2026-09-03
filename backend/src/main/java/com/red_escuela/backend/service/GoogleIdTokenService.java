package com.red_escuela.backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.red_escuela.backend.exception.InvalidGoogleTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Épica 1 Fase 2 (Login con Google): valida el idToken que el frontend recibe de Google contra
 * las claves públicas rotativas de Google (GoogleIdTokenVerifier las refresca solo) — no se
 * hace verificación de JWT a mano, es la única parte de esta feature donde una librería vetada
 * es más segura que código propio.
 */
@Service
public class GoogleIdTokenService {

    private final GoogleIdTokenVerifier verifier;

    public GoogleIdTokenService(@Value("${google.oauth.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
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
