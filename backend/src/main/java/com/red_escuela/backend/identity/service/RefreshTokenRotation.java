package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.dto.AuthTokens;
import com.red_escuela.backend.identity.entity.RefreshToken;
import com.red_escuela.backend.identity.exception.InvalidRefreshTokenException;
import com.red_escuela.backend.identity.repository.RefreshTokenRepository;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class RefreshTokenRotation {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenIssuer authTokenIssuer;
    private final SessionRevoker sessionRevoker;

    public RefreshTokenRotation(RefreshTokenRepository refreshTokenRepository, AuthTokenIssuer authTokenIssuer,
                                SessionRevoker sessionRevoker) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.authTokenIssuer = authTokenIssuer;
        this.sessionRevoker = sessionRevoker;
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthTokens rotate(String rawRefreshToken) {
        String hash = TokenHashUtil.sha256Hex(rawRefreshToken);
        Optional<RefreshToken> stored = refreshTokenRepository.findByTokenHash(hash);
        stored.filter(RefreshToken::getRevoked)
                .filter(rt -> rt.getExpiresAt().isAfter(ApplicationTime.now()))
                .ifPresent(reused -> sessionRevoker.revokeAllSessions(reused.getPerson()));
        RefreshToken existing = stored
                .filter(rt -> !rt.getRevoked())
                .filter(rt -> rt.getExpiresAt().isAfter(ApplicationTime.now()))
                .filter(rt -> rt.getPerson().getStatus() == StatusType.ACTIVE)
                .filter(rt -> refreshTokenRepository.revokeIfActive(hash) > 0)
                .orElseThrow(InvalidRefreshTokenException::new);

        return authTokenIssuer.issueTokens(existing.getPerson());
    }
}
