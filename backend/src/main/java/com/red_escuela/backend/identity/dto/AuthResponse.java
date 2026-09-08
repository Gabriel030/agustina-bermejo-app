package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.usecase.auth.AuthTokens;

public record AuthResponse(String accessToken, String refreshToken, long expiresIn) {

    public static AuthResponse from(AuthTokens tokens) {
        return new AuthResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }
}
