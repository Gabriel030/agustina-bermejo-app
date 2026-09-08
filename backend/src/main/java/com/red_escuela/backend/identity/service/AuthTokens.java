package com.red_escuela.backend.identity.service;

/**
 * Value object con el resultado de login/refresh: access token, refresh token en texto
 * plano (única vez que viaja así) y su duración en segundos.
 */
public record AuthTokens(String accessToken, String refreshToken, long expiresIn) {
}
