package com.red_escuela.backend.identity.usecase.auth;

public record AuthTokens(String accessToken, String refreshToken, long expiresIn) {
}
