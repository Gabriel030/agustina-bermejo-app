package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.infrastructure.AccountAccessStateCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AccessTokenVerifier {
    private final JwtService jwtService;
    private final AccountAccessStateCache accountAccessStateCache;

    public AccessTokenVerifier(JwtService jwtService, AccountAccessStateCache accountAccessStateCache) {
        this.jwtService = jwtService;
        this.accountAccessStateCache = accountAccessStateCache;
    }

    public Optional<AuthenticatedPrincipal> verify(String accessToken) {
        try {
            Claims claims = jwtService.validateAndParse(accessToken);
            List<String> roles = claims.get("roles", List.class);
            Integer claimedTokenVersion = claims.get(JwtService.TOKEN_VERSION_CLAIM, Integer.class);
            int tokenVersion = claimedTokenVersion == null ? 0 : claimedTokenVersion;
            boolean admitted = accountAccessStateCache.find(Long.parseLong(claims.getSubject()))
                    .map(state -> state.admits(tokenVersion))
                    .orElse(false);
            if (!admitted) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedPrincipal(claims.getSubject(), roles == null ? List.of() : roles));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
