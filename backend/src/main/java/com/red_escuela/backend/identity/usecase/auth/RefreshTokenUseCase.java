package com.red_escuela.backend.identity.usecase.auth;

public interface RefreshTokenUseCase {
    AuthTokens refresh(String rawRefreshToken);
}
