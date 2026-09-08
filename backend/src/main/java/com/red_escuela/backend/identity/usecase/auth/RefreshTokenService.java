package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.entity.RefreshToken;
import com.red_escuela.backend.identity.exception.InvalidRefreshTokenException;
import com.red_escuela.backend.identity.repository.RefreshTokenRepository;
import com.red_escuela.backend.identity.service.AuthTokenIssuer;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.util.TokenHashUtil;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class RefreshTokenService implements RefreshTokenUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenIssuer authTokenIssuer;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, AuthTokenIssuer authTokenIssuer) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.authTokenIssuer = authTokenIssuer;
    }

    @Override
    @Transactional
    public AuthTokens refresh(String rawRefreshToken) {
        String hash = TokenHashUtil.sha256Hex(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .filter(rt -> !rt.getRevoked())
                .filter(rt -> rt.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(InvalidRefreshTokenException::new);

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        return authTokenIssuer.issueTokens(existing.getPerson());
    }
}
