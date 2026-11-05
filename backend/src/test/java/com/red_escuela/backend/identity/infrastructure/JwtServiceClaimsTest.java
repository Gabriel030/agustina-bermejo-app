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
        JwtService service = new JwtService(SECRET, 15, "issuer-a");

        Claims first = service.validateAndParse(service.generateAccessToken(person()));
        Claims second = service.validateAndParse(service.generateAccessToken(person()));

        assertThat(first.getIssuer()).isEqualTo("issuer-a");
        assertThat(first.getId()).isNotBlank().isNotEqualTo(second.getId());
    }

    @Test
    void tokenSignedWithOtherIssuerIsRejected() {
        JwtService issuerA = new JwtService(SECRET, 15, "issuer-a");
        JwtService issuerB = new JwtService(SECRET, 15, "issuer-b");
        String token = issuerB.generateAccessToken(person());

        assertThatThrownBy(() -> issuerA.validateAndParse(token)).isInstanceOf(JwtException.class);
    }
}
