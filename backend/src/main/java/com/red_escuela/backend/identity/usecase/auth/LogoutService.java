package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.repository.RefreshTokenRepository;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class LogoutService implements LogoutUseCase {

    private final RefreshTokenRepository refreshTokenRepository;

    public LogoutService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = TokenHashUtil.sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }
}
