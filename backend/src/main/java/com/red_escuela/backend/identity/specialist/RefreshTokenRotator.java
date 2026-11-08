package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.dto.AuthTokens;
import com.red_escuela.backend.identity.dto.ClientContext;
import com.red_escuela.backend.identity.exception.InvalidRefreshTokenException;
import com.red_escuela.backend.identity.repository.RefreshTokenRepository;
import com.red_escuela.backend.identity.repository.RefreshTokenState;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class RefreshTokenRotator {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenRotator.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenIssuer authTokenIssuer;
    private final SessionRevoker sessionRevoker;
    private final RefreshReuseGracePolicy reuseGracePolicy;

    public RefreshTokenRotator(RefreshTokenRepository refreshTokenRepository, AuthTokenIssuer authTokenIssuer,
                                SessionRevoker sessionRevoker, RefreshReuseGracePolicy reuseGracePolicy) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.authTokenIssuer = authTokenIssuer;
        this.sessionRevoker = sessionRevoker;
        this.reuseGracePolicy = reuseGracePolicy;
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthTokens rotate(String rawRefreshToken, ClientContext clientContext) {
        String hash = TokenHashUtil.sha256Hex(rawRefreshToken);
        LocalDateTime now = ApplicationTime.now();
        RefreshTokenState state = refreshTokenRepository.findStateByTokenHash(hash)
                .orElseThrow(InvalidRefreshTokenException::new);

        boolean lostRace = false;
        if (isUsable(state, now)) {
            if (refreshTokenRepository.rotateIfActive(hash, now, clientContext.userAgentHash()) > 0) {
                return authTokenIssuer.issueTokens(state.getPerson(), state.getId());
            }
            lostRace = true;
        }

        RefreshTokenState current = refreshTokenRepository.findStateByTokenHash(hash)
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!current.getRevoked() || !current.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }
        if (lostRace && current.getRotatedAt() == null) {
            throw new InvalidRefreshTokenException();
        }
        return reuseRevokedToken(hash, current, clientContext, now);
    }

    private boolean isUsable(RefreshTokenState state, LocalDateTime now) {
        return !state.getRevoked()
                && state.getExpiresAt().isAfter(now)
                && state.getPerson().getStatus() == StatusType.ACTIVE;
    }

    private AuthTokens reuseRevokedToken(String hash, RefreshTokenState token, ClientContext clientContext, LocalDateTime now) {
        RefreshReuseGracePolicy.Verdict verdict = reuseGracePolicy.evaluate(token, clientContext, now);
        if (verdict == RefreshReuseGracePolicy.Verdict.GRACE_ALLOWED) {
            if (refreshTokenRepository.markGraceUsedIfAvailable(hash) > 0) {
                log.info("Refresh grace used: personId={} tokenId={} secondsSinceRotation={} remoteAddress={}",
                        token.getPerson().getId(), token.getId(),
                        Duration.between(token.getRotatedAt(), now).toSeconds(), clientContext.remoteAddress());
                return authTokenIssuer.issueTokens(token.getPerson(), token.getId());
            }
            verdict = RefreshReuseGracePolicy.Verdict.GRACE_ALREADY_USED;
        }
        log.warn("Refresh token reuse, revoking all sessions: personId={} tokenId={} reason={} remoteAddress={}",
                token.getPerson().getId(), token.getId(), verdict.reason(), clientContext.remoteAddress());
        sessionRevoker.revokeAllSessions(token.getPerson());
        throw new InvalidRefreshTokenException();
    }
}
