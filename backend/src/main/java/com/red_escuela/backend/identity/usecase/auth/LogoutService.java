package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.service.RefreshTokenRevocation;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class LogoutService implements LogoutUseCase {

    private final RefreshTokenRevocation refreshTokenRevocation;

    public LogoutService(RefreshTokenRevocation refreshTokenRevocation) {
        this.refreshTokenRevocation = refreshTokenRevocation;
    }

    @Override
    public void logout(String rawRefreshToken) {
        refreshTokenRevocation.revokeByHash(TokenHashUtil.sha256Hex(rawRefreshToken));
    }
}
