package com.red_escuela.backend.identity.dto;

public record AuthTokens(String accessToken, String refreshToken, long expiresIn) {
}
