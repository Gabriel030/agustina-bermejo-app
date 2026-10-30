package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.infrastructure.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AccessTokenVerifier {
    private final JwtService jwtService;

    public AccessTokenVerifier(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public Optional<AuthenticatedPrincipal> verify(String accessToken) {
        try {
            Claims claims = jwtService.validateAndParse(accessToken);
            List<String> roles = claims.get("roles", List.class);
            return Optional.of(new AuthenticatedPrincipal(claims.getSubject(), roles == null ? List.of() : roles));
        } catch (JwtException e) {
            return Optional.empty();
        }
    }
}
