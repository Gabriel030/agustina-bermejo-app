package com.red_escuela.backend.identity.infrastructure;

import com.red_escuela.backend.identity.entity.Person;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceClaimsTest {

    private static final String SECRET = "secret-long-enough-for-claims-tests-0123456789";

    private Person person() {
        Person person = new Person();
        person.setId(3L);
        person.setRoles(new HashSet<>());
        return person;
    }

    @Test
    void accessTokenCarriesIssuerAndUniqueJti() {
        JwtService service = new JwtService(SECRET, 15, "issuer-a", "agustina-bermejo-app-api", false);

        Claims first = service.validateAndParse(service.generateAccessToken(person()));
        Claims second = service.validateAndParse(service.generateAccessToken(person()));

        assertThat(first.getIssuer()).isEqualTo("issuer-a");
        assertThat(first.getId()).isNotBlank().isNotEqualTo(second.getId());
    }

    @Test
    void tokenSignedWithOtherIssuerIsRejected() {
        JwtService issuerA = new JwtService(SECRET, 15, "issuer-a", "agustina-bermejo-app-api", false);
        JwtService issuerB = new JwtService(SECRET, 15, "issuer-b", "agustina-bermejo-app-api", false);
        String token = issuerB.generateAccessToken(person());

        assertThatThrownBy(() -> issuerA.validateAndParse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void accessTokenCarriesAudience() {
        JwtService service = new JwtService(SECRET, 15, "issuer-a", "aud-a", false);

        assertThat(service.validateAndParse(service.generateAccessToken(person())).getAudience()).containsExactly("aud-a");
    }

    @Test
    void tokenForAnotherAudienceIsRejected() {
        JwtService audienceA = new JwtService(SECRET, 15, "issuer-a", "aud-a", false);
        JwtService audienceB = new JwtService(SECRET, 15, "issuer-a", "aud-b", false);
        String token = audienceB.generateAccessToken(person());

        assertThatThrownBy(() -> audienceA.validateAndParse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenWithoutAudienceIsAcceptedOnlyDuringTheTransitionWindow() {
        String legacy = io.jsonwebtoken.Jwts.builder()
                .issuer("issuer-a")
                .subject("3")
                .expiration(java.util.Date.from(java.time.Instant.now().plusSeconds(60)))
                .signWith(new javax.crypto.spec.SecretKeySpec(sha256(SECRET), "HmacSHA256"), io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();
        JwtService transition = new JwtService(SECRET, 15, "issuer-a", "aud-a", true);
        JwtService strict = new JwtService(SECRET, 15, "issuer-a", "aud-a", false);

        assertThat(transition.validateAndParse(legacy).getSubject()).isEqualTo("3");
        assertThatThrownBy(() -> strict.validateAndParse(legacy)).isInstanceOf(JwtException.class);
    }

    private static byte[] sha256(String secret) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
