package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.repository.RefreshTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RefreshTokenRevocation {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenRevocation(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public void revokeByHash(String tokenHash) {
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            token.setGraceUsed(true);
            refreshTokenRepository.save(token);
            if (token.getParentId() != null) {
                refreshTokenRepository.disableGraceById(token.getParentId());
            }
        });
    }
}
