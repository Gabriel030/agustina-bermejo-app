package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.dto.AuthTokens;
import com.red_escuela.backend.identity.dto.ClientContext;
import com.red_escuela.backend.identity.service.RefreshTokenRotation;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RefreshTokenService implements RefreshTokenUseCase {

    private final RefreshTokenRotation refreshTokenRotation;

    public RefreshTokenService(RefreshTokenRotation refreshTokenRotation) {
        this.refreshTokenRotation = refreshTokenRotation;
    }

    @Override
    public AuthTokens refresh(String rawRefreshToken, ClientContext clientContext) {
        return refreshTokenRotation.rotate(rawRefreshToken, clientContext);
    }
}
