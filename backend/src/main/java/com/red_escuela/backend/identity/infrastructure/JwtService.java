package com.red_escuela.backend.identity.infrastructure;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {
    public static final String TOKEN_VERSION_CLAIM = "tv";

    private final SecretKey signingKey;
    private final long accessTokenExpirationMinutes;
    private final String issuer;
    private final String audience;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-minutes}") long accessTokenExpirationMinutes,
            @Value("${jwt.issuer:agustina-bermejo-app}") String issuer,
            @Value("${jwt.audience:agustina-bermejo-app-api}") String audience) {
        this.issuer = issuer;
        this.audience = audience;
        this.signingKey = deriveSigningKey(JwtSecretPolicy.requireStrong(secret));
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
    }

    private static SecretKey deriveSigningKey(String secret) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    public String generateAccessToken(Person person) {
        Instant now = Instant.now();
        List<String> roles = person.getRoles().stream().map(Role::getName).sorted().toList();

        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(person.getId()))
                .claim("roles", roles)
                .claim(TOKEN_VERSION_CLAIM, person.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(accessTokenExpirationMinutes))))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims validateAndParse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getAccessTokenExpirationMinutes() {
        return accessTokenExpirationMinutes;
    }
}
