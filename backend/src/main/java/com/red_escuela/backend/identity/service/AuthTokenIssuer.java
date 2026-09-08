package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.RefreshToken;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RefreshTokenRepository;
import com.red_escuela.backend.identity.usecase.auth.AuthTokens;
import com.red_escuela.backend.util.TokenHashUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class AuthTokenIssuer {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final SecureTokenGenerator secureTokenGenerator;
    private final long refreshTokenExpirationDays;

    public AuthTokenIssuer(RefreshTokenRepository refreshTokenRepository, JwtService jwtService, SecureTokenGenerator secureTokenGenerator,
                            @Value("${jwt.refresh-token-expiration-days}") long refreshTokenExpirationDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.secureTokenGenerator = secureTokenGenerator;
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
    }

    public AuthTokens issueTokens(Person person) {
        String accessToken = jwtService.generateAccessToken(person);
        String refreshToken = issueRefreshToken(person);
        long expiresIn = Duration.ofMinutes(jwtService.getAccessTokenExpirationMinutes()).toSeconds();
        return new AuthTokens(accessToken, refreshToken, expiresIn);
    }

    private String issueRefreshToken(Person person) {
        String rawToken = secureTokenGenerator.generate();

        RefreshToken entity = new RefreshToken();
        entity.setPerson(person);
        entity.setTokenHash(TokenHashUtil.sha256Hex(rawToken));
        entity.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenExpirationDays));
        entity.setRevoked(false);
        entity.setCreatedAt(LocalDateTime.now());
        refreshTokenRepository.save(entity);

        return rawToken;
    }
}
